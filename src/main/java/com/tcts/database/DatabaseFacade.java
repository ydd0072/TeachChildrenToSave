package com.tcts.database;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.tcts.common.PrettyPrintingDate;
import com.tcts.datamodel.Bank;
import com.tcts.datamodel.BankAdmin;
import com.tcts.datamodel.Event;
import com.tcts.datamodel.School;
import com.tcts.datamodel.SiteStatistics;
import com.tcts.datamodel.Teacher;
import com.tcts.datamodel.User;
import com.tcts.datamodel.Volunteer;
import com.tcts.exception.AllowedDateAlreadyInUseException;
import com.tcts.exception.AllowedTimeAlreadyInUseException;
import com.tcts.exception.EmailAlreadyInUseException;
import com.tcts.exception.InconsistentDatabaseException;
import com.tcts.exception.NoSuchAllowedDateException;
import com.tcts.exception.NoSuchAllowedTimeException;
import com.tcts.exception.NoSuchBankException;
import com.tcts.exception.NoSuchEventException;
import com.tcts.exception.NoSuchSchoolException;
import com.tcts.exception.NoSuchUserException;
import com.tcts.exception.TeacherHasEventsException;
import com.tcts.exception.VolunteerHasEventsException;
import com.tcts.formdata.AddAllowedDateFormData;
import com.tcts.formdata.AddAllowedTimeFormData;
import com.tcts.formdata.CreateBankFormData;
import com.tcts.formdata.CreateEventFormData;
import com.tcts.formdata.CreateSchoolFormData;
import com.tcts.formdata.EditBankFormData;
import com.tcts.formdata.EditPersonalDataFormData;
import com.tcts.formdata.EditSchoolFormData;
import com.tcts.formdata.EditVolunteerPersonalDataFormData;
import com.tcts.formdata.EventRegistrationFormData;
import com.tcts.formdata.SetBankSpecificFieldLabelFormData;
import com.tcts.formdata.TeacherRegistrationFormData;
import com.tcts.formdata.VolunteerRegistrationFormData;

/**
 * Methods for accessing the database. It should probably be refactored somehow.
 */
public interface DatabaseFacade {


    /**
     * Passed the name of a table and field, this returns the maximum allowed length
     * for that field.
     */
    public int getFieldLength(DatabaseField field);

    /**
     * Return the user with this userId, or null if there is none. The actual class
     * returned will be an appropriate subclass of User.
     */
    public User getUserById(String userId) throws SQLException, InconsistentDatabaseException;

    /** Return the user with this email, or null if there is none. */
    public User getUserByEmail(String email) throws SQLException, InconsistentDatabaseException;

    /**
     * Modify certain fields of a User. The user with the userId given in formData
     * will be edited to set their email, firstName, lastName, and phoneNumber to
     * the values given in the other fields of formData. Other properties of a user
     * (such as password and userType) are not modified. The newly modified
     * user is returned.
     */
    public User modifyUserPersonalFields(EditPersonalDataFormData formData)
            throws SQLException, EmailAlreadyInUseException, InconsistentDatabaseException;


    /**
     * Modify normal user fields of a volunteer, and ALSO the bank specific data field.
     */
    public Volunteer modifyVolunteerPersonalFields(EditVolunteerPersonalDataFormData formData)
            throws SQLException, EmailAlreadyInUseException, InconsistentDatabaseException;

    /**
     *   Modifies the schoolId of a teacher in the User table
     * @param userId userId of user to be modified
     * @param organizationId new organzation Id
     * @throws SQLException
     */
    void modifyTeacherSchool(String userId, String organizationId)
            throws SQLException, NoSuchSchoolException, NoSuchUserException;

    /**
     * Insert a new Teacher in the database, and return it. Expects that all
     * fields have been checked for containing valid values. Will throw an
     * exception if the email is not unique or if the school is not found.
     * @throws NoSuchSchoolException 
     * @throws com.tcts.exception.EmailAlreadyInUseException
     * @throws UnsupportedEncodingException 
     * @throws NoSuchAlgorithmException
     */
    public Teacher insertNewTeacher(TeacherRegistrationFormData formData, String hashedPassword, String salt)
            throws SQLException, NoSuchSchoolException, EmailAlreadyInUseException, NoSuchAlgorithmException, UnsupportedEncodingException;


    /**
     *   Returns a list of events created by the passed in teacher.  This list does not have the linked fields filled in.
     * @param teacherId
     * @return A sorted list of Events created by this teacher.  Each event does not have the linked bank and volunteer.
     * @throws SQLException
     */
    public List<Event> getEventsByTeacher(String teacherId) throws SQLException;

    /**
     * Return the list of all events that have null for a volunteer.
     * <p>
     * The performance of this call is key to the performance of the system as a whole,
     * so it has been special-cased a bit. This version promises to return Event objects
     * in which the linkedTeacher has been set, and those teachers will have the
     * linkedSchool set, so that a single call will retrieve all the data needed to
     * display data.
     */
    public List<Event> getAllAvailableEvents() throws SQLException;

    /**
     * Returns a list of events that the passed in volunteer is volunteering for.  Does not fill in the linked fields bank and school.
     * @param volunteerId
     * @return
     * @throws SQLException
     */
    public List<Event> getEventsByVolunteer(String volunteerId) throws SQLException;

    /**Return the list of events that have a particular volunteer.  Fills in the linked teacher and school object */
    public List<Event> getEventsByVolunteerWithTeacherAndSchool (String volunteerId) throws SQLException;


        /**
         * Insert a new Teacher in the database, and return it. Expects that all
         * fields have been checked for containing valid values.
         */
    public void insertEvent(String teacherId, CreateEventFormData formData) throws SQLException;
    
    /**
     * voluneer signup for an event
     * 
     * @param eventId
     * @param volunteerId
     * @throws SQLException
     * @throws NoSuchEventException
     */
    
    public void volunteerForEvent(String eventId, String volunteerId) throws SQLException, NoSuchEventException;

    /** Return the list of volunteers that have a particular bank. */
    public List<Volunteer> getVolunteersByBank(String bankId) throws SQLException;

    /** Returns the Bank Admin for the given bank, or null if that bank has no Bank Admin or there is no such bank. */
    public BankAdmin getBankAdminByBank(String bankId)
            throws SQLException;

    /**
     * Insert a new Volunteer in the database, and return it. Expects that all
     * fields have been checked for containing valid values. Will throw an
     * exception if the email is not unique or if the bank is not found.
     * @throws EmailAlreadyInUseException
     * @throws NoSuchBankException
     */
    public Volunteer insertNewVolunteer(VolunteerRegistrationFormData formData, String hashedPassword, String salt)
            throws SQLException, NoSuchBankException, EmailAlreadyInUseException;

    /** Return the bank with this bankId, or null if there is none. */
    public Bank getBankById(String bankId) throws SQLException;

    /** Return the school with this schoolId, or null if there is none. */
    public School getSchoolById(String schoolId) throws SQLException;

    /** Returns the full list of all schools. */
    public List<School> getAllSchools() throws SQLException;
    
    /** Returns the full list of all banks. */
    public List<Bank> getAllBanks() throws SQLException;

    /** Returns the allowed dates. */
    public List<PrettyPrintingDate> getAllowedDates() throws SQLException;

    /** Returns the allowed times. */
    public List<String> getAllowedTimes() throws SQLException;


    public void deleteSchool(String schoolId) throws SQLException, NoSuchSchoolException;

    /**
     * This will delete a bank. It ALSO deletes the bank admin AND ALL Volunteers belonging
     * to that bank.
     */
    public void deleteBank(String bankId) throws SQLException, NoSuchBankException;
    
    /**
     * Delete volunteer from database. If the volunteer has signed up for any events,
     * it will throw an exception.
     */
    public void deleteVolunteer(String volunteerId) throws SQLException, NoSuchUserException, VolunteerHasEventsException;

    /**
     * Deletes a teacher from the database. If the teacher has any events then it will
     * throw an exception.
     */
    public void deleteTeacher(String teacherId) throws SQLException, NoSuchUserException, TeacherHasEventsException;

    /**
     * This deletes the indicated event, or throws NoSuchEventException if it does not exist.
     */
    public void deleteEvent(String eventId) throws SQLException, NoSuchEventException;

    /**
     * Return the list of all events.
     * <p>
     * To make things faster, this has been special-cased a bit. This call promises
     * to return Event objects in which the linkedTeacher has been set and those
     * teachers have the linkedSchool set; also if the volunteerId for the event is
     * not null then the linkedVolunteer will be set it will have the linkedBank
     * set. This way a single call returns all the data that is needed.
     */
    public List<Event> getAllEvents() throws SQLException, InconsistentDatabaseException;

    /** Returns the indicated event object, or null if it does not exist. */
    public Event getEventById(String eventId) throws SQLException;

    // FIXME: All this stuff shouldn't throw InconsistentDatabaseException.
    
    /**
     * Modifies fields of an existing school. 
     */

	public void modifySchool(EditSchoolFormData school) throws SQLException, NoSuchSchoolException;

    /** Inserts a new bank and the corresponding bank admin. Password is set to null. */
	void insertNewBankAndAdmin(CreateBankFormData formData) throws SQLException, EmailAlreadyInUseException;

    /**
     * Modifies fields of an existing bank. Either modifies the existing bank admin
     * (if there is one) or creates a new Bank Admin (if there wasn't one).
     */
    public void modifyBankAndBankAdmin(EditBankFormData formData)
            throws SQLException, EmailAlreadyInUseException, NoSuchBankException;

    /**
     * Sets a particular field on a bank.
     */
    public void setBankSpecificFieldLabel(SetBankSpecificFieldLabelFormData formData)
            throws SQLException, NoSuchBankException;

    /**
     * Insert new school in database
     * @param school
     * @throws SQLException
     */
    public void insertNewSchool(CreateSchoolFormData school) throws SQLException;

    /** Inserts a new allowed date. */
    public void insertNewAllowedDate(AddAllowedDateFormData formData) throws SQLException, AllowedDateAlreadyInUseException;

    /**
     * Inserts a new allowed time. The name of the time must be unique, and is contained in the
     * allowedTime field of the formData. The timeToInsertBefore field of the form must either
     * contain an empty string (in which case the sort_order will place this after the last
     * existing time) or the name of an existing time (in which case that and all subsequent
     * sort_orders will be incremented to make space for this new one).
     *
     * @throws AllowedTimeAlreadyInUseException if an existing time has the same string as this one
     * @throws NoSuchAllowedTimeException if the timeToInsertBefore field has something that is not
     *   either an existing time or "".
     */
    public void insertNewAllowedTime(AddAllowedTimeFormData formData)
            throws SQLException, AllowedTimeAlreadyInUseException, NoSuchAllowedTimeException;

    public void modifyEvent(EventRegistrationFormData formData) throws SQLException, NoSuchEventException;


	/** Changes the password (and salt) for an existing user. */
	void updateUserCredential(String userId, String hashedPassword, String salt) throws SQLException;
	
	/** This will update reset password link token in db. */
	
	void updateResetPasswordToken(String userId, String resetPasswordToken) throws SQLException;

    /** Update the userStatus field of a specific user. */
    public void updateUserStatusById(String userId, int userStatus) throws SQLException;

	/**
     *		Delete allowed time
     */
    public void deleteAllowedTime(String time) throws SQLException, NoSuchAllowedTimeException;

    /**
     *		Delete allowed date
     */
	public void deleteAllowedDate(PrettyPrintingDate date) throws SQLException, NoSuchAllowedDateException;

    /** Retrieves a bunch of basic statistics about the database. */
    public SiteStatistics getSiteStatistics() throws SQLException;

    /**
     * Get list of teachers with their school details.
     * 
     * @return List<Teacher>
     * @throws SQLException
     */
	public List<Teacher> getTeacherWithSchoolData() throws SQLException;

    /**
     *  Get list of teachers from the specified school
     * @param schoolId
     * @return
     * @throws SQLException
     */
    public List<Teacher> getTeachersBySchool(String schoolId) throws SQLException;


        /**
         * Get list of volunteers with their bank details.
         *
         * @return List<Volunteer>
         * @throws SQLException
         */
	public List<Volunteer> getVolunteersWithBankData() throws SQLException;
	
	/**
	 * Get list of all the teachers whose classes have volunteers.  If there are none returns an empty list.
     * <p>
     * Note: Who designed this? This is logically a mess: teachers can have multiple classes, so
     * teachers are not "matched" or "unmatched". The existing code seems to return a list of EVENTS,
     * with a teacher record for each event (a teacher with multiple events with volunteers would
     * then appear multiple times on the list). The same problem exists for unmatched teachers, matched
     * volunteers, and unmatched volunteers.
	 * @return
	 * @throws SQLException
	 */
	List<Teacher> getMatchedTeachers() throws SQLException;
	
	/**
	 * Get list of all the teachers whose classes dont have volunteers.  If there are no teachers like this returns an empty list.
     * <p>
     * Note: Who designed this? See note on getMatchedTeachers()
	 * @return
	 * @throws SQLException
	 */

	List<Teacher> getUnMatchedTeachers() throws SQLException;
	
	/**
	 * Get list of all the volunteers who have signed-up for classes.  If there are no volunteers returns an empty list.
     * <p>
     * Note: Who designed this? See note on getMatchedTeachers()
	 * @return List<Volunteer>
	 * @throws SQLException
	 */

	List<Volunteer> getMatchedVolunteers() throws SQLException;
	
	/**
	 * Get list of all the volunteers who are not signed up for classes.  If there are no volunteers returns an empty list.
     * <p>
     * Note: Who designed this? See note on getMatchedTeachers()
	 * @return
	 * @throws SQLException
	 */

	List<Volunteer> getUnMatchedVolunteers() throws SQLException;

    /**
     * Returns a list of all the bank admins.  If there are no bankadmins returns an empty list.
     *
     * @return
     * @throws SQLException
     */
    List<BankAdmin> getBankAdmins() throws SQLException;

    /**
     * Retrieves the full collection of all site settings.
     * @return
     * @throws SQLException
     */
    Map<String, String> getSiteSettings() throws SQLException;

    /**
     * This modifies a specific site setting. If the existing setting name already
     * exists, then this modifies it; if it does not exist then this creates a
     * new setting.
     *
     * @param settingName the new site setting to modify or create
     * @param settingName the new value for the site setting
     * @throws SQLException
     */
    void modifySiteSetting(String settingName, String settingValue) throws SQLException;

}
