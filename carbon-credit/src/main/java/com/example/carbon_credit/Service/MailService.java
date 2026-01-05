package com.example.carbon_credit.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;  // THÊM: Import cho log
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;  // THÊM: Import cho JavaMailSender
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);  // THÊM: Khai báo log

    @Autowired
    private MailSender mailSender;

    @Value("${spring.mail.username}")
    private String systemEmail;

    public void sendConfirmRoleEmail(String toEmail, String requestedRole, String confirmLink) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(systemEmail);
        mail.setTo(toEmail);
        mail.setSubject("Confirm your role request");
        mail.setText(
                "You have requested role: " + requestedRole + "\n\n" +
                        "Please confirm your request by clicking the link below:\n" +
                        confirmLink + "\n\n" +
                        "This link will expire in 15 minutes."
        );

        try {
            mailSender.send(mail);
            log.info("Email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Email send failed: " + e.getMessage());
        }
    }

    // Trong sendApproveResult
    public void sendApproveResult(String toEmail, String Role) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(systemEmail);
        message.setTo(toEmail);
        message.setSubject("Role Request Approved!");
        message.setText(
                "Dear User,\n\n" +
                        "Your request for role '" + Role + "' has been approved by admin.\n" +
                        "You can now access OWNER features.\n\n" +
                        "Thank you!\nCarbon Credit Team"
        );

        try {
            mailSender.send(message);
            log.info("Approve email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send approve email to {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Approve email failed: " + e.getMessage());
        }
    }

    // Trong sendRejectRole
    public void sendRejectRole(String toEmail, String Role) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(systemEmail);
        message.setTo(toEmail);
        message.setSubject("Role Request Rejected");
        message.setText(
                "Dear User,\n\n" +
                        "Your request for role '" + Role + "' has been rejected.\n" +
                        "Reason: [Add reason if available]\n\n" +
                        "Please try again or contact support.\n\n" +
                        "Thank you!\nCarbon Credit Team"
        );

        try {
            mailSender.send(message);
            log.info("Reject email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send reject email to {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Reject email failed: " + e.getMessage());
        }
    }

    public void sendVerifyProject(String toEmail, String projectName){
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(systemEmail);
        message.setTo(toEmail);
        message.setSubject("The project has been verified");
        message.setText(
                "Dear Owner,\n\n" +
                        "Your request for project '" + projectName + "' has been verified.\n" +
                        "Please try again or contact support.\n\n" +
                        "Thank you!\nCarbon Credit Team"
        );

        try {
            mailSender.send(message);
            log.info(toEmail);
        }catch (Exception e){
            log.error("fail to send email to {}: {}", toEmail, e.getMessage(), e);

        }
    }

    public void sendApproveProject(String toEmail, String projectName){
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(systemEmail);
        message.setTo(toEmail);
        message.setSubject("\n" +
                "Congratulations, the project has been approved");
        message.setText(
                "Dear Owner,\n\n" +
                        "Your request for project '" + projectName + "' has been approved.\n" +
                        "Please try again or contact support.\n\n" +
                        "Thank you!\nCarbon Credit Team"
        );

        try {
            mailSender.send(message);
            log.info(toEmail);
        }catch (Exception e){
            log.error("fail to send email to {}: {}", toEmail, e.getMessage(), e);

        }
    }
    public void sendRejectProject(String toEmail, String projectName, String reason){
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(systemEmail);
        message.setTo(toEmail);
        message.setSubject("\n" +
                "The project has been rejected");
        message.setText(
                "Dear Owner,\n\n" +
                        "Your request for project '" + projectName + "' has been reject.\n" +
                        "Reason: [Add reason if available]\n\n" + reason +
                        "Please try again or contact support.\n\n" +
                        "Thank you!\nCarbon Credit Team"
        );

        try {
            mailSender.send(message);
            log.info(toEmail);
        }catch (Exception e){
            log.error("fail to send email to {}: {}", toEmail, e.getMessage(), e);

        }
    }
}