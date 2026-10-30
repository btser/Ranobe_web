package main.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/** Thảo luận độc lập với bình luận truyện; chỉ lưu và xuất văn bản. */
@Service
public class DiscussionService {
    private final JdbcTemplate jdbc;
    public DiscussionService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public record Topic(long id, int userId, String userName, String title, String content,
                        boolean notification, long views, Instant createdAt, Instant updatedAt,
                        boolean deleted, boolean locked, long commentCount,
                        Instant activityAt, int lastUserId, String lastUserName) {}
    public record Reply(long id, long topicId, int userId, String userName, String content,
                        Instant createdAt, Instant updatedAt, boolean deleted) {}

    private static final String TOPICS = """
            SELECT d.*,u.user_name,
                (SELECT COUNT(*) FROM comments c WHERE c.discuss_id=d.discuss_id AND c.is_deleted=FALSE) AS comment_count,
                COALESCE(last_reply.created_at,d.created_at) AS activity_at,
                COALESCE(last_reply.user_id,d.user_id) AS last_user_id,
                COALESCE(last_reply.user_name,u.user_name) AS last_user_name
            FROM discuss d JOIN users u ON u.user_id=d.user_id
            LEFT JOIN LATERAL (
                SELECT c.created_at,c.user_id,cu.user_name FROM comments c JOIN users cu ON cu.user_id=c.user_id
                WHERE c.discuss_id=d.discuss_id AND c.is_deleted=FALSE
                ORDER BY c.created_at DESC,c.comment_id DESC LIMIT 1
            ) last_reply ON TRUE
            """;

    public static int page(int page) { return Math.min(100000,Math.max(0,page)); }
    public static String category(String type) {
        return "notification".equals(type) || "discussion".equals(type) ? type : "all";
    }
    public static String visibility(String state, boolean staff) {
        return staff && ("all".equals(state)||"hidden".equals(state)) ? state : "active";
    }
    public static String keyword(String q) {
        String value=q==null?"":q.strip(); return value.substring(0,Math.min(150,value.length()));
    }

    public Page<Topic> topics(String query, String type, String state, boolean staff, int page) {
        type=category(type); state=visibility(state,staff); page=page(page);
        String pattern="%"+keyword(query).toLowerCase(java.util.Locale.ROOT)
                .replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        String where=" WHERE LOWER(d.title) LIKE ? ESCAPE '!'"
                + ("notification".equals(type)?" AND d.notification=TRUE":"discussion".equals(type)?" AND d.notification=FALSE":"")
                + ("all".equals(state)?"":"hidden".equals(state)?" AND d.is_deleted=TRUE":" AND d.is_deleted=FALSE");
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM discuss d"+where,Long.class,pattern);
        List<Topic> items=jdbc.query(TOPICS+where+" ORDER BY activity_at DESC,d.discuss_id DESC LIMIT 24 OFFSET ?",
                (r,n)->topic(r),pattern,page*24);
        return new PageImpl<>(items,PageRequest.of(page,24),total==null?0:total);
    }

    public Topic topic(long id, boolean staff) {
        List<Topic> result=jdbc.query(TOPICS+" WHERE d.discuss_id=?"+(staff?"":" AND d.is_deleted=FALSE"),
                (r,n)->topic(r),id);
        if(result.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Không tìm thấy chủ đề.");
        return result.get(0);
    }
    private static Topic topic(java.sql.ResultSet r) throws java.sql.SQLException {
        return new Topic(r.getLong("discuss_id"),r.getInt("user_id"),r.getString("user_name"),r.getString("title"),r.getString("content"),
                r.getBoolean("notification"),r.getLong("views"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("last_updated").toInstant(),
                r.getBoolean("is_deleted"),r.getBoolean("is_locked"),r.getLong("comment_count"),r.getTimestamp("activity_at").toInstant(),
                r.getInt("last_user_id"),r.getString("last_user_name"));
    }

    public Page<Reply> replies(long id, boolean staff, int page) {
        topic(id,staff); page=page(page);
        String where=" WHERE c.discuss_id=?"+(staff?"":" AND c.is_deleted=FALSE");
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM comments c"+where,Long.class,id);
        List<Reply> items=jdbc.query("SELECT c.*,u.user_name FROM comments c JOIN users u ON u.user_id=c.user_id"+where
                +" ORDER BY c.created_at,c.comment_id LIMIT 20 OFFSET ?",(r,n)->reply(r),id,page*20);
        return new PageImpl<>(items,PageRequest.of(page,20),total==null?0:total);
    }
    public Reply reply(long topicId,long commentId,boolean staff) {
        topic(topicId,staff);
        var items=jdbc.query("SELECT c.*,u.user_name FROM comments c JOIN users u ON u.user_id=c.user_id WHERE c.discuss_id=? AND c.comment_id=?"
                +(staff?"":" AND c.is_deleted=FALSE"),(r,n)->reply(r),topicId,commentId);
        if(items.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Không tìm thấy bình luận.");
        return items.get(0);
    }
    private static Reply reply(java.sql.ResultSet r) throws java.sql.SQLException {
        return new Reply(r.getLong("comment_id"),r.getLong("discuss_id"),r.getInt("user_id"),r.getString("user_name"),r.getString("content"),
                r.getTimestamp("created_at").toInstant(),r.getTimestamp("last_updated").toInstant(),r.getBoolean("is_deleted"));
    }

    public void view(long id) {
        jdbc.update("UPDATE discuss SET views=views+1 WHERE discuss_id=? AND is_deleted=FALSE",id);
    }

    /** Khóa người dùng trước khi kiểm tra khoảng chờ, ngăn hai lần gửi song song vượt giới hạn. */
    private boolean activeUser(int userId) {
        var roles=jdbc.queryForList("""
                SELECT a.role FROM accounts a JOIN users u ON u.user_id=a.user_id
                WHERE u.user_id=? AND u.is_deleted=FALSE AND a.is_deleted=FALSE FOR UPDATE OF u,a
                """,String.class,userId);
        if(roles.isEmpty()) throw error(HttpStatus.FORBIDDEN,"Tài khoản không còn quyền thao tác.");
        return roles.contains("ADMIN")||roles.contains("MOD");
    }
    private Topic lockedTopic(long id,boolean staff) {
        if(jdbc.queryForList("SELECT discuss_id FROM discuss WHERE discuss_id=? FOR UPDATE",Long.class,id).isEmpty()) {
            throw error(HttpStatus.NOT_FOUND,"Không tìm thấy chủ đề.");
        }
        return topic(id,staff);
    }
    private static String text(String value,int max,String field) {
        String result=value==null?"":value.strip();
        if(result.isEmpty()||result.length()>max) throw error(HttpStatus.BAD_REQUEST,field+" cần 1–"+max+" ký tự.");
        return result;
    }
    private static ResponseStatusException error(HttpStatus status,String message) { return new ResponseStatusException(status,message); }

    @Transactional
    public long create(int userId,String title,String content,boolean notification) {
        title=text(title,200,"Tiêu đề");content=text(content,20000,"Nội dung");
        boolean staff=activeUser(userId);
        if(notification&&!staff) throw error(HttpStatus.FORBIDDEN,"Chỉ Mod/Admin được đăng thông báo.");
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM discuss WHERE user_id=? AND created_at>CURRENT_TIMESTAMP-INTERVAL '60 seconds')",Boolean.class,userId))) {
            throw error(HttpStatus.TOO_MANY_REQUESTS,"Hãy chờ 60 giây giữa hai chủ đề.");
        }
        return jdbc.queryForObject("INSERT INTO discuss(user_id,title,content,notification) VALUES (?,?,?,?) RETURNING discuss_id",Long.class,userId,title,content,notification);
    }

    @Transactional
    public void editTopic(long id,int userId,String title,String content,boolean notification) {
        title=text(title,200,"Tiêu đề");content=text(content,20000,"Nội dung");
        boolean staff=activeUser(userId);Topic topic=lockedTopic(id,staff);
        if(topic.userId()!=userId||topic.deleted()) throw error(HttpStatus.FORBIDDEN,"Chỉ người tạo được sửa chủ đề đang hiện của mình.");
        if(notification!=topic.notification()&&!staff) throw error(HttpStatus.FORBIDDEN,"Chỉ Mod/Admin được đổi loại thông báo.");
        jdbc.update("UPDATE discuss SET title=?,content=?,notification=?,last_updated=CURRENT_TIMESTAMP WHERE discuss_id=?",title,content,notification,id);
    }

    @Transactional
    public long postReply(long id,int userId,String content) {
        content=text(content,5000,"Bình luận");
        boolean staff=activeUser(userId);Topic topic=lockedTopic(id,staff);
        if(topic.deleted()||topic.locked()) throw error(HttpStatus.CONFLICT,"Chủ đề đã ẩn hoặc khóa trả lời.");
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM comments WHERE user_id=? AND created_at>CURRENT_TIMESTAMP-INTERVAL '10 seconds')",Boolean.class,userId))) {
            throw error(HttpStatus.TOO_MANY_REQUESTS,"Hãy chờ 10 giây giữa hai bình luận.");
        }
        return jdbc.queryForObject("INSERT INTO comments(discuss_id,user_id,content) VALUES (?,?,?) RETURNING comment_id",Long.class,id,userId,content);
    }

    @Transactional
    public void editReply(long id,long commentId,int userId,String content) {
        content=text(content,5000,"Bình luận");
        boolean staff=activeUser(userId);Topic topic=lockedTopic(id,staff);
        Reply reply=reply(id,commentId,staff);
        if(topic.deleted()||reply.deleted()||reply.userId()!=userId) throw error(HttpStatus.FORBIDDEN,"Chỉ người viết được sửa bình luận đang hiện của mình.");
        jdbc.update("UPDATE comments SET content=?,last_updated=CURRENT_TIMESTAMP WHERE comment_id=?",content,commentId);
    }

    @Transactional
    public void moderate(long id,int userId,String action,Long commentId) {
        if(!activeUser(userId)) throw error(HttpStatus.FORBIDDEN,"Chỉ Mod/Admin được quản lý thảo luận.");
        lockedTopic(id,true);
        switch(action) {
            case "hide" -> jdbc.update("UPDATE discuss SET is_deleted=TRUE WHERE discuss_id=?",id);
            case "restore" -> jdbc.update("UPDATE discuss SET is_deleted=FALSE WHERE discuss_id=?",id);
            case "lock" -> jdbc.update("UPDATE discuss SET is_locked=TRUE WHERE discuss_id=?",id);
            case "unlock" -> jdbc.update("UPDATE discuss SET is_locked=FALSE WHERE discuss_id=?",id);
            case "hide-comment", "restore-comment" -> {
                if(commentId==null) throw error(HttpStatus.BAD_REQUEST,"Thiếu mã bình luận.");
                reply(id,commentId,true);
                jdbc.update("UPDATE comments SET is_deleted=? WHERE discuss_id=? AND comment_id=?","hide-comment".equals(action),id,commentId);
            }
            default -> throw error(HttpStatus.BAD_REQUEST,"Tác vụ không hợp lệ.");
        }
    }
}
