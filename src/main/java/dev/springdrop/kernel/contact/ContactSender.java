package dev.springdrop.kernel.contact;

/** Who sent a contact message: the name and address they gave, their account, and where they sent it from. */
public record ContactSender(String name, String mail, long accountId, String ip) {
}
