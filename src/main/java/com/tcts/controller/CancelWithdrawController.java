package com.tcts.controller;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.tcts.exception.EmailNotSentException;
import com.tcts.exception.NoVolunteerOnThatEventException;
import com.tcts.util.NewEmailUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.tcts.common.SessionData;
import com.tcts.database.DatabaseFacade;
import com.tcts.datamodel.Event;
import com.tcts.datamodel.Teacher;
import com.tcts.datamodel.User;
import com.tcts.datamodel.Volunteer;
import com.tcts.exception.AppConfigurationException;
import com.tcts.exception.InvalidParameterFromGUIException;
import com.tcts.exception.NoSuchEventException;
import com.tcts.exception.NotLoggedInException;
import com.tcts.exception.NotOwnedByYouException;
import com.tcts.formdata.WithdrawFormData;
import com.tcts.util.EmailUtil;
import com.tcts.util.TemplateUtil;


/**
 * Used for a teacher to cancel a class or a volunteer to cancel their registration
 * for a course.
 */
@Controller
public class CancelWithdrawController {

    @Autowired
    private DatabaseFacade database;
    
    @Autowired
    private TemplateUtil templateUtil;
    
    @Autowired
    private EmailUtil emailUtil;

    @Autowired
    private NewEmailUtil newEmailUtil;

    /**
     * A volunteer withdraws themselves from a class they had previously
     * volunteered for.
     */
    @RequestMapping(value = "volunteerWithdraw", method = RequestMethod.GET)
    public String showVolunteerWithdraw(
            HttpSession session,
            Model model,
            @RequestParam(value = "eventId") String eventId
        ) throws SQLException
    {
        // --- Ensure logged in as a Volunteer ---
        SessionData sessionData = SessionData.fromSession(session);
        Volunteer loggedInVolunteer = sessionData.getVolunteer();
        if (loggedInVolunteer == null) {
            throw new NotLoggedInException();
        }

        // --- Ensure the event is valid and is listed for this volunteer ---
        if (eventId == null || eventId.length() == 0) {
            throw new InvalidParameterFromGUIException();
        }
        Event event = database.getEventById(eventId);
        if (event == null) {
            // No such event by that ID
            throw new InvalidParameterFromGUIException();
        }
        if (!loggedInVolunteer.getUserId().equals(event.getVolunteerId())) {
            throw new NotOwnedByYouException();
        }

        // --- Display the page ---
        WithdrawFormData formData = new WithdrawFormData();
        formData.setEventId(eventId);
        model.addAttribute("formData", formData);
        model.addAttribute("errorMessage", "");
        return "volunteerWithdraw";
    }


    /**
     * A volunteer withdraws themselves from a class they had previously
     * volunteered for.
     */
    @RequestMapping(value = "volunteerWithdraw", method = RequestMethod.POST)
    public String doVolunteerWithdraw(
            HttpSession session,
            HttpServletRequest request,
            @ModelAttribute WithdrawFormData formData
        ) throws SQLException
    {
        // --- Ensure logged in as a Volunteer ---
        SessionData sessionData = SessionData.fromSession(session);
        Volunteer loggedInVolunteer = sessionData.getVolunteer();
        if (loggedInVolunteer == null) {
            throw new NotLoggedInException();
        }

        // --- Ensure the event is valid and is listed for this volunteer ---
        String eventId = formData.getEventId();
        if (eventId == null || eventId.length() == 0) {
            throw new InvalidParameterFromGUIException();
        }
        Event event = database.getEventById(eventId);
        if (event == null) {
            // No such event by that ID
            throw new InvalidParameterFromGUIException();
        }
        if (!loggedInVolunteer.getUserId().equals(event.getVolunteerId())) {
            throw new NotOwnedByYouException();
        }

        // --- Perform the withdraw ---
        withdrawFromAnEvent(database, newEmailUtil, event, request);

        // --- Done ---
        return "redirect:" + loggedInVolunteer.getUserType().getHomepage();
    }


    /**
     * Subroutine that actually withdraws a volunteer from an event. Exposed
     * here so it can be shared from other locations.
     * <p>
     * FIXME: I think that this should NOT send an email to the volunteer, but
     * instead to the TEACHER. (The volunteer will be performing the action
     * or be notified by some other means. However, I will not make that change
     * right at the moment.
     */
    public static void withdrawFromAnEvent(
            DatabaseFacade database,
            NewEmailUtil newEmailUtil,
            Event event,
            HttpServletRequest request
        ) throws SQLException
    {
        // --- Make sure we have called it where it makes sense ---
        if (event.getVolunteerId() == null) {
            throw new NoVolunteerOnThatEventException();
        }

        // --- Make sure data is loaded ---
        if (event.getLinkedTeacher() == null) {
            Teacher linkedTeacher = (Teacher) database.getUserById((event.getTeacherId()));
            event.setLinkedTeacher(linkedTeacher);
        }

        // --- Save the info needed for emails ---
        String teacherEmail = event.getLinkedTeacher().getEmail();

        // --- Update the database ---
        try {
            database.volunteerForEvent(event.getEventId(), null);
        } catch(NoSuchEventException err) {
            throw new RuntimeException("Shouldn't happen; we just checked if it was there.");
        }

        // --- Send Emails ---
        try {
            Map<String,String> fieldsOfEmail = new HashMap<String,String>();
            fieldsOfEmail.put("classDate", event.getEventDate().getPretty());
            fieldsOfEmail.put("classTime", event.getEventTime());
            newEmailUtil.sendEmail(
                    "Your volunteer for " + event.getEventDate().getPretty() + " cancelled",
                    teacherEmail,
                    null,
                    "volunteerUnregisterEventToTeacher",
                    fieldsOfEmail,
                    request);
        } catch(EmailNotSentException err) {
            // Can't do much about it, so we just ignore it and go on
        }
    }


    /**
     * Display a confirmation page to allow a teacher to cancel a class.
     */
    @RequestMapping(value = "teacherCancel", method = RequestMethod.GET)
    public String showTeacherCancel(
            HttpSession session,
            Model model,
            @RequestParam(value = "eventId") String eventId
    ) throws SQLException
    {
        // --- Ensure logged in as a Teacher ---
        SessionData sessionData = SessionData.fromSession(session);
        Teacher loggedInTeacher = sessionData.getTeacher();
        if (loggedInTeacher == null) {
            throw new NotLoggedInException();
        }

        // --- Ensure the event is valid and is owned by this teacher ---
        if (eventId == null || eventId.length() == 0) {
            throw new InvalidParameterFromGUIException();
        }
        Event event = database.getEventById(eventId);
        if (event == null) {
            // No such event by that ID
            throw new InvalidParameterFromGUIException();
        }
        if (!loggedInTeacher.getUserId().equals(event.getTeacherId())) {
            throw new NotOwnedByYouException();
        }

        // --- Display the page ---
        WithdrawFormData formData = new WithdrawFormData();
        formData.setEventId(eventId);
        model.addAttribute("formData", formData);
        model.addAttribute("hasVolunteer", event.getVolunteerId() != null);
        model.addAttribute("errorMessage", "");
        return "teacherCancel";
    }


    /**
     * A volunteer withdraws themselves from a class they had previously
     * volunteered for.
     */
    @RequestMapping(value = "teacherCancel", method = RequestMethod.POST)
    public String doTeacherCancel(
            HttpSession session,
            HttpServletRequest request,
            @ModelAttribute WithdrawFormData formData
    ) throws SQLException
    {
        // --- Ensure logged in as a Teacher ---
        SessionData sessionData = SessionData.fromSession(session);
        Teacher loggedInTeacher = sessionData.getTeacher();
        if (loggedInTeacher == null) {
            throw new NotLoggedInException();
        }

        // --- Ensure the event is valid and is owned by this teacher ---
        String eventId = formData.getEventId();
        if (eventId == null || eventId.length() == 0) {
            throw new InvalidParameterFromGUIException();
        }
        Event event = database.getEventById(eventId);
        if (event == null) {
            // No such event by that ID
            throw new InvalidParameterFromGUIException();
        }
        if (!loggedInTeacher.getUserId().equals(event.getTeacherId())) {
            throw new NotOwnedByYouException();
        }

        // --- Update the database ---
        try {
            database.deleteEvent(eventId);
        } catch(NoSuchEventException err) {
            throw new RuntimeException("Shouldn't happen; we just checked if it was there.");
        }

        // --- Send Emails ---
        
        if (event.getVolunteerId() != null) {
        	
        	User volunteer = database.getUserById(event.getVolunteerId());
        	try {
        	
            	Map<String,Object> emailModel = new <String, Object>HashMap();
            	String logoImage =  request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/tcts/img/logo-tcts.png";;
        		
        		emailModel.put("logoImage", logoImage);
//            	emailModel.put("to", volunteer.getEmail()); // FIXME: Restore
                System.out.println("SENDING EMAIL TO mcherm@mcherm.com INSTEAD OF '" + volunteer.getEmail() + "'."); // FIXME: Remove
                emailModel.put("to", "mcherm@mcherm.com"); // FIXME: Remove
                emailModel.put("subject", "Your volunteer event has been canceled.");
            	emailModel.put("class", "<br/>" + event.getEventId() + " - " + event.getEventDate() + " - " + event.getEventTime() + " - " + event.getNotes() + "<br/>");
            	String singupUrl =  request.getRequestURL() + "/register.htm";
            	emailModel.put("signupLink", singupUrl);
            	emailModel.put("teacher", "<br/>" + loggedInTeacher.getFirstName() + " - " + loggedInTeacher.getLastName() + " - " + loggedInTeacher.getPhoneNumber() + " - " + loggedInTeacher.getEmail() + "<br/>");
            	String emailContent = templateUtil.generateTemplate("volunteerSignUpToVolunteer", emailModel);
                emailUtil.sendEmail(emailContent, emailModel);
            } catch(AppConfigurationException err) {
                // FIXME: Need to log or report this someplace more reliable.
                System.err.println("Could not send email for new volunteer '" + volunteer.getEmail() + "'.");
            } catch(IOException err) {
                // FIXME: Need to log or report this someplace more reliable.
                System.err.println("Could not send email for new volunteer '" + volunteer.getEmail() + "'.");
            }
        }
        // FIXME: Here we should send an email to the site admin

        // --- Done ---
        return "redirect:" + loggedInTeacher.getUserType().getHomepage();
    }


}
