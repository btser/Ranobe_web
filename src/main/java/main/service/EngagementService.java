package main.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class EngagementService {
    public static final long VIEW_WEIGHT = 1;
    public static final long COMMENT_WEIGHT = 10;
    public static final long VOTE_WEIGHT = 100;
    private final JdbcTemplate jdbc;
    public EngagementService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Signals(long views, long comments, long votes) {
        public long score() { return Math.addExact(Math.addExact(Math.multiplyExact(views, VIEW_WEIGHT), Math.multiplyExact(comments, COMMENT_WEIGHT)), Math.multiplyExact(votes, VOTE_WEIGHT)); }
    }
    public record Ranked(int productId, String title, String coverImage, long views, long comments, long votes, long score) {
        public String path() { return "/truyen/" + productId + "/" + main.util.Slugs.slug(title, "truyen"); }
    }
    public record Comment(long id, int userId, String userName, String body, Instant createdAt, boolean banned) {}

    public void recordView(int productId, String viewerKey, Instant now) {
        jdbc.update("""
                INSERT INTO product_views(product_id,viewer_key,view_bucket,viewed_at)
                SELECT product_id,?,?,? FROM products WHERE product_id=? AND status='PUBLIC' AND is_deleted=FALSE
                ON CONFLICT DO NOTHING
                """, viewerKey, now.getEpochSecond()/1800, Timestamp.from(now), productId);
    }

    public Signals signals(int productId) {
        return jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM product_views WHERE product_id=?),
                       (SELECT COUNT(*) FROM product_comments WHERE product_id=?),
                       (SELECT COUNT(*) FROM product_votes WHERE product_id=?)
                """, (r,n) -> new Signals(r.getLong(1), r.getLong(2), r.getLong(3)), productId, productId, productId);
    }

    public boolean voted(int productId, int userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM product_votes WHERE product_id=? AND user_id=?)",
                Boolean.class, productId, userId));
    }

    @Transactional
    public void vote(int productId, int userId, boolean selected) {
        requireActiveUser(userId);
        requirePublicProduct(productId);
        if (selected) jdbc.update("INSERT INTO product_votes(product_id,user_id) VALUES (?,?) ON CONFLICT DO NOTHING", productId, userId);
        else jdbc.update("DELETE FROM product_votes WHERE product_id=? AND user_id=?", productId, userId);
    }

    @Transactional
    public void comment(int productId, int userId, String body) {
        if (body == null || body.isBlank() || body.strip().length()>2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bình luận cần 1–2000 ký tự.");
        }
        requireActiveUser(userId);
        requirePublicProduct(productId);
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM product_comments WHERE user_id=? AND created_at>CURRENT_TIMESTAMP-INTERVAL '10 seconds')",
                Boolean.class, userId))) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Hãy chờ 10 giây giữa hai bình luận.");
        jdbc.update("INSERT INTO product_comments(product_id,user_id,body) VALUES (?,?,?)", productId, userId, body.strip());
    }

    private void requireActiveUser(int userId) {
        var active = jdbc.queryForList("""
                SELECT u.user_id FROM users u JOIN accounts a ON a.user_id=u.user_id
                WHERE u.user_id=? AND u.is_deleted=FALSE AND a.is_deleted=FALSE FOR UPDATE OF u
                """, Integer.class, userId);
        if (active.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không còn quyền thao tác.");
    }

    private void requirePublicProduct(int productId) {
        if (jdbc.queryForList("SELECT product_id FROM products WHERE product_id=? AND status='PUBLIC' AND is_deleted=FALSE FOR UPDATE",
                Integer.class, productId).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Truyện không còn công khai.");
    }

    public List<Comment> comments(int productId, int page) {
        return jdbc.query("""
                SELECT c.comment_id,u.user_id,u.user_name,c.body,c.created_at,u.is_deleted FROM product_comments c
                JOIN users u ON u.user_id=c.user_id WHERE c.product_id=? ORDER BY c.created_at DESC,c.comment_id DESC LIMIT 20 OFFSET ?
                """, (r,n) -> new Comment(r.getLong(1),r.getInt(2),r.getString(3),r.getString(4),r.getTimestamp(5).toInstant(),r.getBoolean(6)),
                productId, Math.max(0, Math.min(page,100000))*20);
    }

    public List<Ranked> ranking(Instant since, int limit, boolean requireActivity) {
        Timestamp cutoff = Timestamp.from(since);
        return jdbc.query("""
                WITH counts AS (
                    SELECT p.product_id,p.title,p.cover_image,
                    (SELECT COUNT(*) FROM product_views v WHERE v.product_id=p.product_id AND v.viewed_at>=?) AS views,
                    (SELECT COUNT(*) FROM product_comments c WHERE c.product_id=p.product_id AND c.created_at>=?) AS comments,
                    (SELECT COUNT(*) FROM product_votes v WHERE v.product_id=p.product_id AND v.created_at>=?) AS votes
                    FROM products p WHERE p.status='PUBLIC' AND p.is_deleted=FALSE
                ), scores AS (SELECT *,views*?+comments*?+votes*? AS score FROM counts)
                SELECT * FROM scores WHERE (?=FALSE OR score>0) ORDER BY score DESC,votes DESC,views DESC,product_id DESC LIMIT ?
                """, (r,n) -> new Ranked(r.getInt("product_id"),r.getString("title"),r.getString("cover_image"),
                        r.getLong("views"),r.getLong("comments"),r.getLong("votes"),r.getLong("score")),
                cutoff,cutoff,cutoff,VIEW_WEIGHT,COMMENT_WEIGHT,VOTE_WEIGHT,requireActivity,Math.max(1,Math.min(limit,100)));
    }
}
