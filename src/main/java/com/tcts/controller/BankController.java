package com.tcts.controller;

import java.sql.SQLException;
import java.util.List;

import javax.servlet.http.HttpSession;

import com.tcts.datamodel.BankAdmin;
import com.tcts.exception.EmailAlreadyInUseException;
import com.tcts.exception.InvalidParameterFromGUIException;
import com.tcts.exception.NoSuchBankException;
import com.tcts.exception.NotLoggedInException;
import com.tcts.formdata.CreateBankFormData;
import com.tcts.formdata.EditBankFormData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.tcts.common.SessionData;
import com.tcts.database.DatabaseFacade;
import com.tcts.datamodel.Bank;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * This is a controller for the pages that are used by the site admin to edit the
 * list of banks (and bank admin users).
 */
@Controller
public class BankController {

    @Autowired
    private DatabaseFacade database;
    
    /**
     * Render the bank edit page.
     */
    @RequestMapping(value = "viewEditBanks", method = RequestMethod.GET)
    public String showBanks(HttpSession session, Model model) throws SQLException {
        SessionData sessionData = SessionData.fromSession(session);
        if (sessionData.getSiteAdmin() == null) {
            throw new NotLoggedInException();
        }

        return showForm(model);
    }


    /**
     * A subroutine used to set up and then show the edit banks form. It
     * returns the string, so you can invoke it as "return showForm(...)".
     */
    public String showForm(Model model) throws SQLException {
        List<Bank> banks = database.getAllBanks();
        for (Bank bank : banks) {
            BankAdmin bankAdmin = (BankAdmin) database.getUserById(bank.getBankAdminId());
            bank.setLinkedBankAdmin(bankAdmin);
        }

        model.addAttribute("banks", banks);
        return "banks";
    }


    /**
     * Deletes a bank and all users associated with it.
     */
    @RequestMapping(value = "deleteBank", method = RequestMethod.POST)
    public String deleteBank(
            @RequestParam String bankId,
            HttpSession session,
            Model model
        ) throws SQLException
    {
        SessionData sessionData = SessionData.fromSession(session);
        if (sessionData.getSiteAdmin() == null) {
            throw new NotLoggedInException();
        }

        try {
            database.deleteBank(bankId);
        } catch(NoSuchBankException err) {
            throw new InvalidParameterFromGUIException();
        }

        return showForm(model);
    }


    @RequestMapping(value = "editBank", method = RequestMethod.GET)
    public String enterDataToEditBank(
            HttpSession session,
            Model model,
            @RequestParam String bankId
    ) throws SQLException {
        // --- Ensure logged in ---
        SessionData sessionData = SessionData.fromSession(session);
        if (sessionData.getSiteAdmin() == null) {
            throw new NotLoggedInException();
        }

        // --- Load existing data ---
        Bank bank = database.getBankById(bankId);
        EditBankFormData formData = new EditBankFormData();
        formData.setBankId(bankId);
        formData.setBankName(bank.getBankName());
        if (bank.getBankAdminId() != null) {
            BankAdmin bankAdmin = (BankAdmin) database.getUserById(bank.getBankAdminId());
            formData.setFirstName(bankAdmin.getFirstName());
            formData.setLastName(bankAdmin.getLastName());
            formData.setEmail(bankAdmin.getEmail());
            formData.setPhoneNumber(bankAdmin.getPhoneNumber());
        }

        // --- Show the edit page ---
        return showEditBankWithErrorMessage(model, formData, "");
    }


    /**
     * A subroutine used to set up and then show the add bank form. It
     * returns the string, so you can invoke it as "return showAddBankWithErrorMessage(...)".
     */
    public String showEditBankWithErrorMessage(Model model, EditBankFormData formData, String errorMessage)
            throws SQLException
    {
        model.addAttribute("formData", formData);
        model.addAttribute("errorMessage", errorMessage);
        return "editBank";
    }


    // FIXME: This needs rewriting
    @RequestMapping(value = "editBank", method = RequestMethod.POST)
    public String getUpdatedBank(
            HttpSession session,
            Model model,
            @ModelAttribute("formData") EditBankFormData formData
        ) throws SQLException
    {
        // --- Ensure logged in ---
        SessionData sessionData = SessionData.fromSession(session);
        if (sessionData.getSiteAdmin() == null) {
            throw new NotLoggedInException();
        }

        try {
            database.modifyBankAndBankAdmin(formData);
        } catch(NoSuchBankException err) {
            throw new InvalidParameterFromGUIException();
        } catch(EmailAlreadyInUseException err) {
            return showAddBankWithErrorMessage(model,
                    "That email is already in use; please choose another.");
        }

        // --- Successful; show the master bank edit again ---
        return showForm(model);
    }


    /**
     * Show the page for creating a new bank and bank admin.
     */
    @RequestMapping(value = "addBank", method = RequestMethod.GET)
    public String enterDataToAddBank(
            HttpSession session,
            Model model,
            @ModelAttribute("formData") CreateBankFormData formData
    ) throws SQLException {
        SessionData sessionData = SessionData.fromSession(session);
        if (sessionData.getSiteAdmin() == null) {
            throw new NotLoggedInException();
        }

        // --- Successful; show the edit page again ---
        return showAddBankWithErrorMessage(model, "");
    }

    @RequestMapping(value = "addBank", method = RequestMethod.POST)
    public String doAddBank(
            HttpSession session,
            Model model,
            @ModelAttribute("formData") CreateBankFormData formData
    ) throws SQLException {
        SessionData sessionData = SessionData.fromSession(session);
        if (sessionData.getSiteAdmin() == null) {
            throw new NotLoggedInException();
        }

        try {
            database.insertNewBankAndAdmin(formData);
        } catch(EmailAlreadyInUseException err) {
            return showAddBankWithErrorMessage(model,
                    "That email is already in use; please choose another.");
        }

        // --- Successful; show the master bank edit again ---
        return showForm(model);
    }


    /**
     * A subroutine used to set up and then show the add bank form. It
     * returns the string, so you can invoke it as "return showAddBankWithErrorMessage(...)".
     */
    private String showAddBankWithErrorMessage(Model model, String errorMessage) throws SQLException {
        model.addAttribute("errorMessage", errorMessage);
        return "addBank";
    }


}
