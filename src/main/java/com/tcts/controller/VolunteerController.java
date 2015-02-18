package com.tcts.controller;


import java.sql.SQLException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.tcts.datamodel.Event;
import com.tcts.exception.EmailAlreadyInUseException;
import com.tcts.exception.InconsistentDatabaseException;
import com.tcts.exception.InvalidParameterFromGUIException;
import com.tcts.exception.NoSuchUserException;
import com.tcts.exception.NotLoggedInException;
import com.tcts.exception.VolunteerHasEventsException;
import com.tcts.formdata.EditPersonalDataFormData;

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
import com.tcts.datamodel.User;
import com.tcts.datamodel.Volunteer;

@Controller

public class VolunteerController extends AuthenticationController{

	@Autowired
    private DatabaseFacade database;

    @Autowired
    private NewEmailUtil newEmailUtil;


    /**
     * Render the list of users .
     */
    @RequestMapping(value = "volunteers", method = RequestMethod.GET)
    public String showVolunteersList(HttpSession session, Model model) throws SQLException {
        SessionData sessionData = SessionData.fromSession(session);
        if (sessionData.getSiteAdmin() == null) {
            throw new NotLoggedInException();
        }
        return showVolunteers(model);
    }

    /**
     * A subroutine used to set up and then show the main volunteers list. It returns
     * a string so it can be called as "return showVolunteers(...);".
     */
    public String showVolunteers(Model model) throws SQLException {
        List<Volunteer> volunteers = database.getVolunteerWithBankData();
        model.addAttribute("volunteers", volunteers);
        return "volunteers";
    }


    @RequestMapping(value = "volunteerDelete", method = RequestMethod.POST)
    public String deleteVolunteer(
            @RequestParam String volunteerId,
            HttpSession session,
            Model model,
            HttpServletRequest request)
        throws SQLException
    {
        SessionData sessionData = SessionData.fromSession(session);
        
        if (!sessionData.isAuthenticated()) {
            throw new RuntimeException("Cannot navigate to this page unless you are a logged-in volunteer.");
        }

        // --- First, unregister from any events ---
        List<Event> events = database.getEventsByVolunteer(volunteerId);
        for (Event event : events) {
            CancelWithdrawController.withdrawFromAnEvent(database, newEmailUtil, event, request);
        }

        // --- Now delete the volunteer ---
        try {
			database.deleteVolunteer(volunteerId);
		} catch (NoSuchUserException e) {
			throw new InvalidParameterFromGUIException();
        } catch (VolunteerHasEventsException e) {
            throw new InconsistentDatabaseException("Withdrew from events for a volunteer but some are still there.");
		}
        
        return showVolunteers(model);
    }
    
    @RequestMapping(value = "editVolunteerData", method = RequestMethod.GET)
    public String enterDataToEditVolunteer(
            HttpSession session,
            Model model,
            @RequestParam String userId
    ) throws SQLException {
        // --- Ensure logged in ---
        SessionData sessionData = SessionData.fromSession(session);
        if (sessionData.getSiteAdmin() == null) {
            throw new NotLoggedInException();
        }

        // --- Load existing data ---
        User user = database.getUserById(userId);
        
        // --- Show the edit page ---
        return showEditUserWithErrorMessage(model, transformUserData(user), "");
    }

    /**
     * Actually change the values that were entered.
     */
    @RequestMapping(value="/editVolunteerData", method=RequestMethod.POST)
    public String editVolunteerData(
            HttpSession session,
            Model model,
            @ModelAttribute("formData") EditPersonalDataFormData formData
        ) throws SQLException
    {
        SessionData sessionData = SessionData.fromSession(session);
        // FIXME: must validate the fields
        User user = sessionData.getUser();
        if (user == null) {
            throw new NotLoggedInException();
        }

        try {
           database.modifyUserPersonalFields(user.getUserId(), formData);
        } catch(EmailAlreadyInUseException err) {
            return showEditUserWithErrorMessage(model, formData, "That email is already in use by another user.");
        }

        return showVolunteers(model);
    }
    
    /**
     * A subroutine used to set up and then show the add user form. It
     * returns the string, so you can invoke it as "return showEditSchoolWithErrorMessage(...)".
     */
    public String showEditUserWithErrorMessage(Model model, EditPersonalDataFormData formData, String errorMessage)
            throws SQLException
    {
        model.addAttribute("formData", formData);
        model.addAttribute("errorMessage", errorMessage);
        return "editVolunteer";
    }
    
    /**
     * Transform user modal data
     */
    private EditPersonalDataFormData transformUserData(User user) {
    	EditPersonalDataFormData formData = new EditPersonalDataFormData();
        formData.setEmail(user.getEmail());
        formData.setFirstName(user.getFirstName());
        formData.setLastName(user.getLastName());
        formData.setPhoneNumber(user.getPhoneNumber());
        return formData;
    }

	    
}
