

import jakarta.persistence.*;

@Entity
@Table(name = "chapters")
public class Chapter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chapter_id")
    private int chapterId;

    @Column(name = "volume_id")
    private int volumeId;

    @Lob
    private String context;

    public Chapter() {}

    public Chapter(int chapterId, int volumeId, String context) {
        this.chapterId = chapterId;
        this.volumeId = volumeId;
        this.context = context;
    }

    public int getChapterId() { return chapterId; }
    public int getVolumeId() { return volumeId; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
}