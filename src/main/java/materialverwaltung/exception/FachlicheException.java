package materialverwaltung.exception;

import java.io.Serial;
import java.util.Objects;

public class FachlicheException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final FachlicherFehler fehler;

    public FachlicheException(FachlicherFehler fehler, String message) {
        super(message);
        this.fehler = Objects.requireNonNull(fehler);
    }

    public FachlicheException(
            FachlicherFehler fehler, String message, Throwable cause){
        super(message, cause);
        this.fehler = Objects.requireNonNull(fehler);
    }

    public FachlicherFehler getFehler() {
        return fehler;
    }
}
