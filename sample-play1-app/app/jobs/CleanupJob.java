package jobs;

import play.Logger;
import play.cache.Cache;
import play.jobs.Job;
import play.jobs.OnApplicationStop;

@OnApplicationStop
public class CleanupJob extends Job {

    public void doJob() {
        Cache.clear();
        Logger.info("Cleanup job running before application stop");
    }
}
