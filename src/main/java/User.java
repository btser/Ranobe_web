
import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private int userId;

    @Column(name = "user_name", columnDefinition = "VARCHAR(100)")
    private String userName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Temporal(TemporalType.DATE)
    private Date birth;

    @Lob
    private String hobby;

    @Lob
    private String introduce;

    public User() {}

    public User(int userId, String userName, String avatarUrl, Date birth, String hobby, String introduce) {
        this.userId = userId;
        this.userName = userName;
        this.avatarUrl = avatarUrl;
        this.birth = birth;
        this.hobby = hobby;
        this.introduce = introduce;
    }

    public int getUserId() { return userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public Date getBirth() { return birth; }
    public String getHobby() { return hobby; }
    public String getIntroduce() { return introduce; }
}