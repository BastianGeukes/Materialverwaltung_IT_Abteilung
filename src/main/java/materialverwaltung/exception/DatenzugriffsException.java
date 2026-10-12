package materialverwaltung.exception;

import java.io.Serial;
import java.sql.SQLException;
import java.util.Objects;

public class DatenzugriffsException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public DatenzugriffsException(String message, SQLException cause) {
        super(message, Objects.requireNonNull(cause));
    }

    public String getSqlState() {
        return ((SQLException) getCause()).getSQLState();
    }
}
