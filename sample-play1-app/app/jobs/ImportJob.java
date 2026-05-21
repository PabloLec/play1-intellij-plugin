package jobs;

import play.Logger;
import play.jobs.Every;
import play.jobs.Job;

@Every("1h")
public class ImportJob extends Job {

    public void doJob() {
        Logger.info("Import job running every hour");
    }
}
