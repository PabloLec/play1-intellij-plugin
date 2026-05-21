package jobs;

import play.Logger;
import play.Play;
import play.jobs.Job;
import play.jobs.OnApplicationStart;

@OnApplicationStart
public class BootstrapJob extends Job {

    public void doJob() {
        Logger.info("Bootstrap job starting, blog title=%s",
            Play.configuration.getProperty("blog.title"));
    }
}
