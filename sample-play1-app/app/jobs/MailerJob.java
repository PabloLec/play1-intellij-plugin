package jobs;

import play.Logger;
import play.jobs.Job;

public class MailerJob extends Job {

    private final String recipient;

    public MailerJob(String recipient) {
        this.recipient = recipient;
    }

    public void doJob() {
        Logger.info("Mailer job sending to %s", recipient);
    }
}
