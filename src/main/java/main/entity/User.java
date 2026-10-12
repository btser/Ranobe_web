package main.entity;

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

    @Column(columnDefinition = "TEXT")
    private String hobby;

    @Column(columnDefinition = "TEXT")
    private String introduce;

    @Column(name = "cover_url", columnDefinition = "TEXT")
    private String coverUrl;

    public User() {}

    public User(String userName, String avatarUrl, Date birth, String hobby, String introduce) {
        this.userName = userName;
        this.avatarUrl = avatarUrl;
        this.birth = birth;
        this.hobby = hobby;
        this.introduce = introduce;
    }

    @Column(name = "is_deleted")
    private boolean isDeleted = false;
    
    public boolean isDeleted() { return isDeleted;}
    public int getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getAvatarUrl() { return avatarUrl; }
    public Date getBirth() { return birth; }
    public String getHobby() { return hobby; }
    public String getIntroduce() { return introduce; }
    public String getCoverUrl() { return coverUrl; }


    public void setUserId(int userId) { this.userId = userId; }
    public void setBirth(Date birth) { this.birth = birth; }
    public void setHobby(String hobby) { this.hobby = hobby; }
    public void setIntroduce(String introduce) { this.introduce = introduce; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public void setUserName(String userName) { this.userName = userName; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
}
