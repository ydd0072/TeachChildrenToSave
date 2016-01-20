package com.tcts.controller;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.tcts.common.PrettyPrintingDate;
import com.tcts.common.SessionData;
import com.tcts.database.DatabaseFacade;
import com.tcts.datamodel.Event;
import com.tcts.datamodel.Teacher;
import com.tcts.datamodel.User;
import com.tcts.datamodel.Volunteer;
import com.tcts.exception.AppConfigurationException;
import com.tcts.exception.InvalidParameterFromGUIException;
import com.tcts.exception.NoSuchEventException;
import com.tcts.exception.NoVolunteerOnThatEventException;
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

    /**
     *   A volunteer is withdrawn from a class
     *   This method can be called by a volunteer or bankadmin to withdraw themselves from a class
     *   they signed up for or by a siteadmin in which case they are withdrawing the current volunteer from the class
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
        Volunteer loggedInVolunteer;
        if (sessionData.getVolunteer()!= null){
            loggedInVolunteer = sessionData.getVolunteer();
        } else if (sessionData.getBankAdmin()!=null){
            loggedInVolunteer = sessionData.getBankAdmin();
        } else if (sessionData.getSiteAdmin()!=null){
            loggedInVolunteer = null;
        } else {
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
        if ((sessionData.getVolunteer()!= null  || sessionData.getBankAdmin()!= null)
                && (!loggedInVolunteer.getUserId().equals(event.getVolunteerId()))) {
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

        String eventId = formData.getEventId();
        if (eventId == null || eventId.length() == 0) {
            throw new InvalidParameterFromGUIException();
        }

        Event event = database.getEventById(eventId);
        if (event == null) {
            // No such event by that ID
            throw new InvalidParameterFromGUIException();
        }

        if ((sessionData.getVolunteer()!= null)) {
            //if a volunteer is logged make sure the volunteer owns the event she is withdrawing from
            if (!sessionData.getVolunteer().getUserId().equals(event.getVolunteerId())) {
                throw new NotOwnedByYouException();
            }
        } else if ((sessionData.getBankAdmin()!= null)) {
            //if a bankadmin is logged in make sure the bankadmin  owns the event she is withdrawing from
            if (!sessionData.getBankAdmin().getUserId().equals(event.getVolunteerId())) {
                throw new NotOwnedByYouException();
            }
       } else if (sessionData.getSiteAdmin() == null) { //siteadmin has permission to withdraw other volunteer's events and we don't do the "owned by you" check
            //if a bankadmin, volunteer, or siteadmin is not logged in
            throw new NotLoggedInException();
        }

        // --- Perform the withdraw ---
        withdrawFromAnEvent(database, templateUtil, emailUtil, event, request, formData.getWithdrawNotes());

        // --- Done ---
        return "redirect:" + sessionData.getUser().getUserType().getHomepage();   }


    /**
     * Subroutine that actually withdraws a volunteer from an event. Exposed
     * here so it can be shared from other locations.
     * <p>
    */
    public static void withdrawFromAnEvent(
            DatabaseFacade database,
            TemplateUtil templateUtil,
            EmailUtil emailUtil,
            Event event,
            HttpServletRequest request,
            String withdrawNotes
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
        User teacher = database.getUserById(event.getVolunteerId());
        User volunteer= database.getUserById(event.getTeacherId());

        // --- Update the database ---
        try {
            database.volunteerForEvent(event.getEventId(), null);
        } catch(NoSuchEventException err) {
            throw new RuntimeException("Shouldn't happen; we just checked if it was there.");
        }

        // --- Send Emails ---
        // Send email to Teacher
        try {
            Map<String,Object> emailModel = new HashMap<String, Object>();

            String logoImage =  request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/tcts/img/logo-tcts.png";;

            emailModel.put("logoImage", logoImage);
            emailModel.put("to", teacherEmail);
            emailModel.put("subject", "Your volunteer for " + new PrettyPrintingDate(new java.sql.Date(new Date().getTime()))  +" cancelled");
            String htmlTableDataHeader = "<table><tr> " +
					"<td style=\"background-color:#66CCFF\">Class Date</td>" +
                   "<td style=\"background-color:#66CCFF\">Class Time</td>" +
                   "<td style=\"background-color:#66CCFF\">Teacher</td>" +
                   "<td style=\"background-color:#66CCFF\">Volunteer</td>" +
                   "<td style=\"background-color:#66CCFF\">Grade</td>" +
                   "<td style=\"background-color:#66CCFF\">Number of student</td>" +
                   "<td style=\"background-color:#66CCFF\">Class Notes<td/></tr><tr>";
            String htmlTableDataValue = "<td>" + new PrettyPrintingDate(event.getEventDate())  + "</td>" +	
						"<td>" + event.getEventTime()  + "</td>" +
						"<td>" + teacher.getFirstName() + " " + teacher.getLastName()  + "</td>" +
						"<td>" + volunteer.getFirstName() + " " + volunteer.getLastName()  + "</td>" +
						"<td>" + event.getGrade()  + "</td>" +
						"<td>" + event.getNumberStudents()  + "</td>" +
						"<td>" + event.getNotes()  + "</td></tr>";
            emailModel.put("class", htmlTableDataHeader + htmlTableDataValue);
            // FIXME: the email should include formData.getWithdrawNotes()
            String emailContent = templateUtil.generateTemplate("volunteerUnregisterEventToTeacher", emailModel);
            emailUtil.sendEmail(emailContent, emailModel);
        } catch(AppConfigurationException err) {
            // FIXME: Need to log or report this someplace more reliable.
            System.err.println("Could not send email for volunteer withdraw '" + teacherEmail + "'.");
        } catch(IOException err) {
            // FIXME: Need to log or report this someplace more reliable.
            System.err.println("Could not send email for volunteer withdraw '" + teacherEmail + "'.");
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
     * volunteered for.  FIXME: This comment seems wrong
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
        	
            	Map<String,Object> emailModel = new HashMap<String, Object>();
            	String logoImage =  request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/tcts/img/logo-tcts.png";;
        		
        		emailModel.put("logoImage", logoImage);
            	emailModel.put("to", volunteer.getEmail());
            	emailModel.put("subject", "Your volunteer event has been canceled.");
            	String htmlTableDataHeader = "<table><tr> " +
            							"<td style=\"background-color:#66CCFF\">Class Date</td>" +
            	                       "<td style=\"background-color:#66CCFF\">Class Time</td>" +
            	                       "<td style=\"background-color:#66CCFF\">Teacher</td>" +
            	                       "<td style=\"background-color:#66CCFF\">Volunteer</td>" +
            	                       "<td style=\"background-color:#66CCFF\">Grade</td>" +
            	                       "<td style=\"background-color:#66CCFF\">Number of student</td>" +
            	                       "<td style=\"background-color:#66CCFF\">Class Notes<td/></tr><tr>";
            	String htmlTableDataValue = "<td>" + new PrettyPrintingDate(event.getEventDate())  + "</td>" +	
            								"<td>" + event.getEventTime()  + "</td>" +
            								"<td>" + loggedInTeacher.getFirstName() + " " + loggedInTeacher.getLastName()  + "</td>" +
            								"<td>" + volunteer.getFirstName() + " " + volunteer.getLastName()  + "</td>" +
            								"<td>" + event.getGrade()  + "</td>" +
            								"<td>" + event.getNumberStudents()  + "</td>" +
            								"<td>" + event.getNotes()  + "</td></tr>";
            	
            	emailModel.put("class", htmlTableDataHeader + htmlTableDataValue);
            	String singupUrl =  request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/register.htm";
            	emailModel.put("signupLink", singupUrl);
            	
            	String htmlTableDataHeaderForTeacher = "<br/><table><tr> " +
						"<td style=\"background-color:#66CCFF\">Teach Name</td>" +
	                   "<td style=\"background-color:#66CCFF\">Teacher Email Id</td>" +
	                   "<td style=\"background-color:#66CCFF\">Teacher Phone Number</td></tr>";
	                   
	            String htmlTableDataValueForTeacher = "<td>" + loggedInTeacher.getFirstName() + " " + loggedInTeacher.getLastName()  + "</td>" +	
							"<td>" + loggedInTeacher.getEmail()  + "</td>" +
							"<td>" + loggedInTeacher.getPhoneNumber() +"</td></tr>";
	            
            	emailModel.put("teacher", htmlTableDataHeaderForTeacher + htmlTableDataValueForTeacher);
            	String emailContent = templateUtil.generateTemplate("teacherCancelEventToVolunteer", emailModel);
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
