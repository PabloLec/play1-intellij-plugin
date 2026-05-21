package jobs;

import play.Logger;
import play.jobs.Job;
import play.jobs.OnApplicationStop;

@OnApplicationStop
public class CleanupJob extends Job {

    public void doJob() {
        Logger.info("Cleanup job running before application stop");
    }
}
