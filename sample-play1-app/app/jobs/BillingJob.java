package jobs;

import play.Logger;
import play.jobs.Job;
import play.jobs.On;

@On("0 0 3 * * ?")
public class BillingJob extends Job {

    public void doJob() {
        Logger.info("Billing job running at scheduled time");
    }
}
