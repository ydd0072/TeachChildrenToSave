package com.tcts.controller;

import java.sql.SQLException;
import java.util.List;

import javax.servlet.http.HttpSession;

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
import com.tcts.exception.EmailAlreadyInUseException;
import com.tcts.exception.InvalidParameterFromGUIException;
import com.tcts.exception.NoSuchUserException;
import com.tcts.exception.NotLoggedInException;
import com.tcts.formdata.EditPersonalDataFormData;

/**
 * This is a controller for the "home page" for users. It renders substantially
 * different information depending on the type of user who is logged in.
 */
@Controller

public class TeacherController {

    @Autowired
    private DatabaseFacade database;
    
    /**
     * Render the list of users .
     */

    @RequestMapping(value = "/teachers", method = RequestMethod.GET)
    public String showTeachersList(HttpSession session, Model model) throws SQLException {
        SessionData sessionData = SessionData.fromSession(session);
        
        if (sessionData.getSiteAdmin() == null) {
            throw new NotLoggedInException();
        }
        List<? super User> teachers = database.getUsersByType("T");
        
        model.addAttribute("teachers", teachers);
        return "teachers";
    }

    @RequestMapping(value = "teacherDelete", method = RequestMethod.GET)
    public String deleteTeacher(@RequestParam String userId,
            HttpSession session,
            Model model) throws SQLException {
        SessionData sessionData = SessionData.fromSession(session);
        
        if (!sessionData.isAuthenticated()) {
            throw new RuntimeException("Cannot navigate to this page unless you are a logged-in volunteer.");
        }
        try {
			database.deleteVolunteer(userId);
		} catch (NoSuchUserException e) {
			throw new InvalidParameterFromGUIException();
		}
        
       model.addAttribute("teachers", database.getUsersByType("T"));
       return "teachers";
    }

    @RequestMapping(value = "editTeacherData", method = RequestMethod.GET)
    public String enterDataToEditTeacher(
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
    @RequestMapping(value="/editTeacherData", method=RequestMethod.POST)
    public String editTeacherData(
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
        
        List<? super User> teachers = database.getUsersByType("T");
        
        model.addAttribute("teachers", teachers);
        return "teachers";
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
        return "editTeacher";
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
