package com.example.conexionfarmaco;

import android.os.AsyncTask;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class MailManager extends AsyncTask<Void, Void, Void> {
    
    private static final String USERNAME = "jeffersonk20castillo@gmail.com";
    private static final String PASSWORD = "birv pbgy jrik hzgu";

    private String destinatario;
    private String subject;
    private String contentHtml;

    public MailManager(String destinatario, String subject, String contentHtml) {
        this.destinatario = destinatario;
        this.subject = subject;
        this.contentHtml = contentHtml;
    }

    // Constructor para compatibilidad con RegistroActivity (Bienvenida)
    public MailManager(String destinatario, String nombreUsuario) {
        this.destinatario = destinatario;
        this.subject = "🏥 ¡Bienvenido/a a Conexión Fármaco!";
        this.contentHtml = "<div style='font-family: Arial, sans-serif; color: #2E4053; border: 1px solid #D6EAF8; padding: 20px; border-radius: 10px;'>" +
                "<h2 style='color: #1B4F72;'>¡Hola, " + nombreUsuario + "!</h2>" +
                "<p>Gracias por unirte a <strong>Conexión Fármaco</strong>, tu plataforma de confianza para la gestión de medicamentos.</p>" +
                "<p>Estamos felices de tenerte con nosotros. Ahora puedes acceder con tu correo y contraseña.</p>" +
                "<br><hr style='border: 0; border-top: 1px solid #D6EAF8;'>" +
                "<p style='font-size: 12px; color: #5DADE2;'>Este es un mensaje automático, por favor no respondas a este correo.</p>" +
                "</div>";
    }

    @Override
    protected Void doInBackground(Void... voids) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new javax.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(USERNAME, PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(USERNAME));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
            message.setSubject(subject);
            message.setContent(contentHtml, "text/html; charset=utf-8");
            Transport.send(message);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
        return null;
    }
}
