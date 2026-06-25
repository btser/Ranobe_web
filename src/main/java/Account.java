
import jakarta.persistence.*;

@Entity
@Table(name = "accounts")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private int accountId;

    private String email;
    private String username;
    private String password;

    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "user_id")
    private User user;

    public Account() {}

    public Account(int accountId, String email, String username, String password, User user) {
        this.accountId = accountId;
        this.email = email;
        this.username = username;
        this.password = password;
        this.user = user;
    }

    public int getAccountId() { return accountId; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public User getUser() { return user; }
}