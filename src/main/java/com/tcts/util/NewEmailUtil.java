package com.tcts.util;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;
import com.tcts.common.Configuration;
import com.tcts.exception.EmailNotSentException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A utility that sends email messages.
 */
@Component
public class NewEmailUtil {

    private Configuration configuration = new Configuration();

    private AmazonSimpleEmailServiceClient emailServiceClient;

    @Autowired
    private TemplateUtil templateUtil;


    /**
     * Initializes the emailServiceClient. After it is created, the client is
     * threadsafe.
     */
    @PostConstruct
    private void initializeEmailServiceClient() {
        AWSCredentials credentials = new BasicAWSCredentials(
                configuration.getProperty("aws.access_key"),
                configuration.getProperty("aws.secret_access_key"));
        emailServiceClient = new AmazonSimpleEmailServiceClient(credentials);
        emailServiceClient.setRegion(Region.getRegion(Regions.US_EAST_1));
    }



    /**
     * This sends an email. The templateName must be the name of one of the email templates
     * that are configured. toAddress is an email address to send to. fields is a map
     * containing the values that will be interpolated into the template (one of which will
     * always be "subject", giving the subject line). It is the
     * caller's responsibility to ensure that all the fields used in the template are
     * present in the map that is provided.
     * <p>
     * If the email cannot be sent for some reason then this throws an exception.
     *
     * @param templateName name of the template to use
     * @param toAddress email address to send to, or null for none
     * @param bccAddresses addresses to BCC, or null for none
     * @param fields values to be interpolated into the template. One of the keys will
     *     always be 'subject'.
     */
    public void sendEmail(
            String subject,
            String toAddress,
            List<String> bccAddresses,
            String templateName,
            Map<String,String> fields,
            HttpServletRequest request // FIXME: We *really* shouldn't pass this in. Find a better way to statically set the server name and port.
    ) throws EmailNotSentException
    {
        // Construct an object to contain the recipient address.
        Destination destination = new Destination();
        if (toAddress != null) {
            destination.setToAddresses(Arrays.asList(toAddress));
        }
        if (bccAddresses != null) {
            destination.setBccAddresses(bccAddresses);
        }

        // Create the subject of the message.
        Content subjectContent = new Content().withData(subject);

        // Create the message body
        Map<String,Object> emailModel = new HashMap<String,Object>();
        emailModel.putAll(fields);
        String logoImage =  request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/tcts/img/logo-tcts.png";
        emailModel.put("logoImage", logoImage);
        String emailBody;
        try {
            emailBody = templateUtil.generateTemplate(templateName, emailModel);
        } catch (Exception err) {
            // FIXME: Should notify that the email wasn't sent. If only we had a log.
            throw new EmailNotSentException(templateName, toAddress, err);
        }
        Body body = new Body().withHtml(new Content().withData(emailBody));

        // Create a message with the specified subject and body.
        Message message = new Message().withSubject(subjectContent).withBody(body);

        // Select the 'from'
        String from = configuration.getProperty("email.from");

        // Attempt to send it
        try {
            emailServiceClient.sendEmail(
                    new SendEmailRequest()
                            .withSource(from)
                            .withDestination(destination)
                            .withMessage(message));
        } catch (Exception err) {
            // FIXME: Should notify that the email wasn't sent. If only we had a log.
            throw new EmailNotSentException(templateName, toAddress, err);
        }
    }

}
