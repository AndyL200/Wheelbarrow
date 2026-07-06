package Components;

import Components.Config.User;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.MediaPlayer;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurface;
import uk.co.caprica.vlcj.media.callback.AbstractCallbackMedia;
import uk.co.caprica.vlcj.media.callback.DefaultCallbackMedia;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import com.sun.jna.Pointer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.awt.GraphicsDevice;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FrameGrabber;

public class VideoCallComp extends BorderPane {
    PipedInputStream in;
    PipedOutputStream out; 
    EmbeddedMediaPlayer mediaPlayer;
    ImageView imageView;
    int idx;
    
    public VideoCallComp(int idx) {
        this.idx = idx;
        this.getStyleClass().add("video-call-comp");
        this.setMaxHeight(Double.MAX_VALUE);
        this.setMaxWidth(Double.MAX_VALUE);
        
        MediaPlayerFactory factory = new MediaPlayerFactory();
        this.mediaPlayer = factory.mediaPlayers().newEmbeddedMediaPlayer();

        /* 
        this.mediaPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            public void playing(MediaPlayer mediaPlayer) {
            }

            public void paused(MediaPlayer mediaPlayer) {
            }

            public void stopped(MediaPlayer mediaPlayer) {
            }

            public void timeChanged(MediaPlayer mediaPlayer, long newTime) {
            }
        });
        */
        
        this.imageView = new ImageView();
        this.imageView.setPreserveRatio(true);
        this.imageView.fitWidthProperty().bind(this.widthProperty());
        this.imageView.fitHeightProperty().bind(this.heightProperty());

        this.mediaPlayer.videoSurface().set(new ImageViewVideoSurface(this.imageView));

        this.in = new PipedInputStream();
        try {
            this.out = new PipedOutputStream(in);
            initVideoStream();
        } catch (IOException e) {
            System.err.println("Error initializing video stream: " + e.getMessage());
            //[TODO] handle this better
            this.out = new PipedOutputStream();
        }

        this.onMouseEnteredProperty().addListener((obs, oldVal, newVal) -> {
                buildOverlay();
        });
        this.onMouseExitedProperty().addListener((obs, oldVal, newVal) -> {
                //remove overlay
                this.setLeft(null);
                this.setRight(null);
                this.setTop(null);
                this.setBottom(null);
        });

        this.setCenter(this.imageView);

    }


    public PipedOutputStream getVideoOutputStream() {
        return out;
    }

    public int getClientId() {
        return idx;
    }

    private void buildOverlay() {

    }

    private void initVideoStream() throws IOException {
        mediaPlayer.media().play(new VideoStreamMedia(in));
    }
}

class VideoStreamMedia extends AbstractCallbackMedia {
    private PipedInputStream videoStream;

    public VideoStreamMedia(PipedInputStream videoStream) {
        super(false); //not seekable
        this.videoStream = videoStream;
    }

    @Override
    protected long onGetSize() {
        return -1; // unknown size — it's a live stream
    }

    @Override
    protected int onRead(Pointer buffer, int bufferSize) {
        byte[] bytes = new byte[bufferSize];
        try {
            int bytesRead = videoStream.read(bytes, 0, bufferSize);
            if (bytesRead == -1) return -1; // EOF
            buffer.write(0, bytes, 0, bytesRead);
            return bytesRead;
        } catch (IOException e) {
            return -1;
        }
    }

    @Override
    protected boolean onSeek(long offset) {
        return false; // not seekable — live pipe
    }

    @Override
    protected boolean onOpen() {
        return videoStream != null; // stream is ready
    }

    @Override
    public void onClose() {
        try {
            videoStream.close();
        } catch (IOException e) {
            System.err.println("Error closing video stream: " + e.getMessage());
        }
    }
}
