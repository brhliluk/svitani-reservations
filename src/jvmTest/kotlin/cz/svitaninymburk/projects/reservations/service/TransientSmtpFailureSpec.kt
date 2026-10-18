package cz.svitaninymburk.projects.reservations.service

import jakarta.mail.internet.AddressException
import jakarta.mail.internet.InternetAddress
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException
import org.eclipse.angus.mail.smtp.SMTPSendFailedException
import org.eclipse.angus.mail.util.MailConnectException
import org.eclipse.angus.mail.util.SocketConnectException
import org.simplejavamail.MailException
import org.simplejavamail.api.mailer.config.TransportStrategy
import org.simplejavamail.email.EmailBuilder
import org.simplejavamail.mailer.MailerBuilder
import java.net.ConnectException
import java.net.ServerSocket
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Rozhoduje o tom, jestli se odeslání zopakuje. Špatně vyhodnocená trvalá chyba by
 * každý požadavek prodloužila o sedm sekund čekání na výsledek, který se nezmění.
 */
class TransientSmtpFailureSpec {

    /** Přesně to, co přišlo z Gmailu 21. 9. 2026 a připravilo zákazníka o potvrzení. */
    private fun gmail451() = SMTPSendFailedException(
        "DATA", 451, "451-4.3.0 Mail server temporarily rejected message.", null, arrayOf(), arrayOf(), arrayOf(),
    )

    private fun wrapped(cause: Throwable): MailException =
        object : MailException("Third party error", cause) {}

    @Test
    fun `SMTP 4xx is retried`() {
        assertTrue(wrapped(gmail451()).isTransientSmtpFailure())
    }

    @Test
    fun `SMTP 5xx is not retried`() {
        val permanent = SMTPSendFailedException(
            "DATA", 550, "550-5.7.1 Message rejected", null, arrayOf(), arrayOf(), arrayOf(),
        )
        assertFalse(wrapped(permanent).isTransientSmtpFailure())
    }

    @Test
    fun `temporary rejection of a specific address is retried`() {
        val greylisted = SMTPAddressFailedException(
            InternetAddress("kdo@example.com"), "RCPT TO", 450, "450 4.2.0 Try again later",
        )
        assertTrue(wrapped(greylisted).isTransientSmtpFailure())
    }

    @Test
    fun `connection errors are retried`() {
        val refused = MailConnectException(SocketConnectException("Connection refused", ConnectException(), "smtp.gmail.com", 465, 30_000))
        assertTrue(wrapped(refused).isTransientSmtpFailure())
        assertTrue(wrapped(SocketTimeoutException("Read timed out")).isTransientSmtpFailure())
    }

    @Test
    fun `bad address is not retried`() {
        assertFalse(wrapped(AddressException("Illegal semicolon, not in group")).isTransientSmtpFailure())
    }

    @Test
    fun `unknown error is not retried`() {
        assertFalse(wrapped(IllegalStateException("něco jiného")).isTransientSmtpFailure())
    }

    /** Skutečné odeslání na zavřený port — ověří, že `sendBlocking` pustí ven řetězec, ve kterém retry chybu najde. */
    @Test
    fun `real connection failure surfaces as a transient MailException`() {
        val closedPort = ServerSocket(0).use { it.localPort }
        val mailer = MailerBuilder
            .withSMTPServer("127.0.0.1", closedPort)
            .withTransportStrategy(TransportStrategy.SMTP)
            .buildMailer()
        val email = EmailBuilder.startingBlank()
            .from("Svítání", "odesilatel@example.com")
            .addTo("prijemce@example.com")
            .withSubject("Test")
            .withPlainText("Test")
            .buildEmail()

        val failure = assertFailsWith<MailException> { mailer.sendBlocking(email) }
        assertTrue(failure.isTransientSmtpFailure(), failure.fullMessage())
    }
}
