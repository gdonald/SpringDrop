package dev.springdrop.kernel.flood;

import java.time.Duration;

/**
 * How much of one thing a site puts up with before it stops answering: how many
 * attempts, and over how long. The defaults follow Drupal's: a handful of tries
 * against one account, more from one address, since an address may be shared by
 * a whole office.
 */
public interface FloodSettings {

    String LOGIN_USER = "user.failed_login_user";

    String LOGIN_IP = "user.failed_login_ip";

    String PASSWORD_RESET = "user.password_reset";

    int USER_THRESHOLD = 5;

    Duration USER_WINDOW = Duration.ofHours(6);

    int IP_THRESHOLD = 50;

    Duration IP_WINDOW = Duration.ofHours(1);

    int RESET_THRESHOLD = 5;

    Duration RESET_WINDOW = Duration.ofHours(1);
}
