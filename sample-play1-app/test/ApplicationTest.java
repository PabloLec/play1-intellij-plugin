import org.junit.Test;
import play.test.*;
import play.mvc.*;
import play.mvc.Http.*;

/**
 * Sample functional test for the Play 1 Toolkit sample app.
 * This file is a fixture — it is not executed by the plugin test suite.
 */
public class ApplicationTest extends FunctionalTest {

    @Test
    public void testThatIndexPageWorks() {
        Response response = GET("/");
        assertIsOk(response);
        assertContentType("text/html", response);
        assertCharset(play.Play.defaultWebEncoding, response);
    }
}
