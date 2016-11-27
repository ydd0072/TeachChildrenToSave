package com.tcts.database;

import com.tcts.common.PrettyPrintingDate;
import com.tcts.datamodel.Bank;
import com.tcts.datamodel.BankAdmin;
import com.tcts.datamodel.Event;
import com.tcts.datamodel.School;
import com.tcts.datamodel.SiteAdmin;
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

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An implementation of the database that is maintained entirely in memory. When updates
 * are made it manages a thread which writes the changes out to a different database
 * implementation. This should make all queries much, much faster.
 */
public class InMemoryWriteBehindDatabase implements DatabaseFacade {

    private final DatabaseFacade backingDB;


    private final Map<DatabaseField, Integer> fieldLengthByField;
    private final Map<String, String> siteSettings;
    private final List<PrettyPrintingDate> allowedDates;
    private final List<String> allowedTimes;

    private final Map<String, Event> eventById;
    private final Map<String, List<Event>> eventsByTeacher;
    private final Map<String, List<Event>> eventsByVolunteer;
    private final List<Event> availableEvents;

    private final Map<String, Bank> bankById;
    private final Map<String, School> schoolById;

    private final Map<String, User> userById;
    private final Map<String, User> userByEmail;
    private final List<Teacher> matchedTeachers;
    private final List<Teacher> unmatchedTeachers;
    private final List<Volunteer> matchedVolunteers;
    private final List<Volunteer> unmatchedVolunteers;
    private final List<BankAdmin> bankAdmins;
    private final List<SiteAdmin> siteAdmins; // initialized when used
    private final Map<String, List<Volunteer>> volunteersByBank;


    // ===== Constructor that Initializes the In-Memory Copy =====

    /**
     * Constructor which points to a backing DB, initializes the entire in-memory
     * schema of data, and begins a thread for writing data out.
     *
     * @param backingDB
     */
    public InMemoryWriteBehindDatabase(DatabaseFacade backingDB) throws SQLException {
        this.backingDB = backingDB;

        // --- Initialize databaseFields ---
        fieldLengthByField = new HashMap<DatabaseField, Integer>();
        for (DatabaseField databaseField : DatabaseField.values()) {
            fieldLengthByField.put(databaseField, backingDB.getFieldLength(databaseField));
        }

        // --- Initialize SiteSettings ---
        siteSettings = new HashMap<String,String>();
        siteSettings.putAll(backingDB.getSiteSettings());

        // --- Initialize AllowedDates ---
        allowedDates = new ArrayList<PrettyPrintingDate>();
        allowedDates.addAll(backingDB.getAllowedDates());

        // --- Initialize AllowedTimes ---
        allowedTimes = new ArrayList<String>();
        allowedTimes.addAll(backingDB.getAllowedTimes());

        // --- Initialize Events ---
        eventById = new HashMap<String,Event>();
        eventsByTeacher = new HashMap<String,List<Event>>();
        eventsByVolunteer = new HashMap<String,List<Event>>();
        availableEvents = new ArrayList<Event>();
        List<Event> events = backingDB.getAllEvents();
        for (Event event : events) {
            eventById.put(event.getEventId(), event);
            // -- by Teacher --
            List<Event> eventsForThisTeacher = eventsByTeacher.get(event.getTeacherId());
            if (eventsForThisTeacher == null) {
                eventsForThisTeacher = new ArrayList<Event>();
            }
            eventsForThisTeacher.add(event);
            eventsByTeacher.put(event.getTeacherId(), eventsForThisTeacher);
            // -- by Volunteer --
            if (event.getVolunteerId() != null) {
                List<Event> eventsForThisVolunteer = eventsByVolunteer.get(event.getVolunteerId());
                if (eventsForThisVolunteer == null) {
                    eventsForThisVolunteer = new ArrayList<Event>();
                }
                eventsForThisVolunteer.add(event);
                eventsByVolunteer.put(event.getVolunteerId(), eventsForThisVolunteer);
            }
            // -- availableEvents --
            if (event.getVolunteerId() == null) {
                availableEvents.add(event);
            }
        }

        // --- Initialize Banks ---
        bankById = new HashMap<String, Bank>();
        for (Bank bank : backingDB.getAllBanks()) {
            bankById.put(bank.getBankId(), bank);
        }

        // --- Initialize Schools ---
        schoolById = new HashMap<String, School>();
        for (School school : backingDB.getAllSchools()) {
            schoolById.put(school.getSchoolId(), school);
        }

        // --- Initialize Users ---
        // - create empty structures -
        userById = new HashMap<String, User>();
        userByEmail = new HashMap<String, User>();
        matchedTeachers = new ArrayList<Teacher>();
        unmatchedTeachers = new ArrayList<Teacher>();
        matchedVolunteers = new ArrayList<Volunteer>();
        unmatchedVolunteers = new ArrayList<Volunteer>();
        bankAdmins = new ArrayList<BankAdmin>();
        siteAdmins = new ArrayList<SiteAdmin>();
        volunteersByBank = new HashMap<String,List<Volunteer>>();
        // - populate lists -
        matchedTeachers.addAll(backingDB.getMatchedTeachers());
        unmatchedTeachers.addAll(backingDB.getUnMatchedTeachers());
        matchedVolunteers.addAll(backingDB.getMatchedVolunteers());
        unmatchedVolunteers.addAll(backingDB.getUnMatchedVolunteers());
        bankAdmins.addAll(backingDB.getBankAdmins());
        // Note: cannot populate siteAdmins so they become a special case, populated when they log in
        // - populate indexes -
        for (Teacher teacher : matchedTeachers) {
            userById.put(teacher.getUserId(), teacher);
            userByEmail.put(teacher.getEmail(), teacher);
        }
        for (Teacher teacher : unmatchedTeachers) {
            userById.put(teacher.getUserId(), teacher);
            userByEmail.put(teacher.getEmail(), teacher);
        }
        for (Volunteer volunteer : matchedVolunteers) {
            userById.put(volunteer.getUserId(), volunteer);
            userByEmail.put(volunteer.getEmail(), volunteer);
            List<Volunteer> volunteersForThisBank = volunteersByBank.get(volunteer.getBankId());
            if (volunteersForThisBank == null) {
                volunteersForThisBank = new ArrayList<Volunteer>();
            }
            volunteersForThisBank.add(volunteer);
            volunteersByBank.put(volunteer.getBankId(), volunteersForThisBank);
        }
        for (Volunteer volunteer : unmatchedVolunteers) {
            userById.put(volunteer.getUserId(), volunteer);
            userByEmail.put(volunteer.getEmail(), volunteer);
            List<Volunteer> volunteersForThisBank = volunteersByBank.get(volunteer.getBankId());
            if (volunteersForThisBank == null) {
                volunteersForThisBank = new ArrayList<Volunteer>();
            }
            volunteersForThisBank.add(volunteer);
            volunteersByBank.put(volunteer.getBankId(), volunteersForThisBank);
        }
    }

    // ===== Updating the Backing Store =====

    /** Just a placeholder:
     * FIXME: Find all these and actually do it.
     */
    private void needsToUpdateInMemoryData() {
    }

    /**
     * This is a placeholder for where the actual updates will happen.
     * FIXME: Needs to eventually do something.
     */
    private void needsUpdate() {
    }


    // ===== Methods from Database Facade =====

    @Override
    public int getFieldLength(DatabaseField field) {
        return fieldLengthByField.get(field);
    }

    @Override
    public User getUserById(String userId) throws SQLException, InconsistentDatabaseException {
        return userById.get(userId);
    }

    @Override
    public User getUserByEmail(String email) throws SQLException, InconsistentDatabaseException {
        return userByEmail.get(email);
    }

    @Override
    public User modifyUserPersonalFields(EditPersonalDataFormData formData) throws SQLException, EmailAlreadyInUseException, InconsistentDatabaseException {
        needsToUpdateInMemoryData();
        needsUpdate();
        return null;
    }

    @Override
    public Volunteer modifyVolunteerPersonalFields(EditVolunteerPersonalDataFormData formData) throws SQLException, EmailAlreadyInUseException, InconsistentDatabaseException {
        needsToUpdateInMemoryData();
        needsUpdate();
        return null;
    }

    @Override
    public void modifyTeacherSchool(String userId, String organizationId) throws SQLException, NoSuchSchoolException, NoSuchUserException {
        needsToUpdateInMemoryData();
        needsUpdate();
    }

    @Override
    public Teacher insertNewTeacher(TeacherRegistrationFormData formData, String hashedPassword, String salt) throws SQLException, NoSuchSchoolException, EmailAlreadyInUseException, NoSuchAlgorithmException, UnsupportedEncodingException {
        needsToUpdateInMemoryData();
        needsUpdate();
        return null;
    }

    @Override
    public List<Event> getEventsByTeacher(String teacherId) throws SQLException {
        return Collections.unmodifiableList(eventsByTeacher.get(teacherId));
    }

    @Override
    public List<Event> getAllAvailableEvents() throws SQLException {
        return Collections.unmodifiableList(availableEvents);
    }

    @Override
    public List<Event> getEventsByVolunteer(String volunteerId) throws SQLException {
        return Collections.unmodifiableList(eventsByVolunteer.get(volunteerId));
    }

    @Override
    public List<Event> getEventsByVolunteerWithTeacherAndSchool(String volunteerId) throws SQLException {
        return Collections.unmodifiableList(eventsByVolunteer.get(volunteerId));
    }

    @Override
    public void insertEvent(String teacherId, CreateEventFormData formData) throws SQLException {
        needsToUpdateInMemoryData();
        needsUpdate();
    }

    @Override
    public void volunteerForEvent(String eventId, String volunteerId) throws SQLException, NoSuchEventException {
        needsToUpdateInMemoryData();
        needsUpdate();
    }

    @Override
    public List<Volunteer> getVolunteersByBank(String bankId) throws SQLException {
        return Collections.unmodifiableList(volunteersByBank.get(bankId));
    }

    @Override
    public BankAdmin getBankAdminByBank(String bankId) throws SQLException {
        Bank bank = bankById.get(bankId);
        if (bank == null) {
            return null;
        }
        else {
            return bank.getLinkedBankAdmin();
        }
    }

    @Override
    public Volunteer insertNewVolunteer(VolunteerRegistrationFormData formData, String hashedPassword, String salt) throws SQLException, NoSuchBankException, EmailAlreadyInUseException {
        needsToUpdateInMemoryData();
        needsUpdate();
        return null;
    }

    @Override
    public Bank getBankById(String bankId) throws SQLException {
        return bankById.get(bankId);
    }

    @Override
    public School getSchoolById(String schoolId) throws SQLException {
        return schoolById.get(schoolId);
    }

    @Override
    public List<School> getAllSchools() throws SQLException {
        List<School> result = new ArrayList<School>();
        result.addAll(schoolById.values());
        return result;
    }

    @Override
    public List<Bank> getAllBanks() throws SQLException {
        List<Bank> result = new ArrayList<Bank>();
        result.addAll(bankById.values());
        return result;
    }

    @Override
    public List<PrettyPrintingDate> getAllowedDates() throws SQLException {
        return Collections.unmodifiableList(allowedDates);
    }

    @Override
    public List<String> getAllowedTimes() throws SQLException {
        return Collections.unmodifiableList(allowedTimes);
    }

    @Override
    public void deleteSchool(String schoolId) throws SQLException, NoSuchSchoolException {
        needsToUpdateInMemoryData();
        needsUpdate();
    }

    @Override
    public void deleteBank(String bankId) throws SQLException, NoSuchBankException {
        needsToUpdateInMemoryData();
        needsUpdate();
    }

    @Override
    public void deleteVolunteer(String volunteerId) throws SQLException, NoSuchUserException, VolunteerHasEventsException {
        needsToUpdateInMemoryData();
        needsUpdate();
    }

    @Override
    public void deleteTeacher(String teacherId) throws SQLException, NoSuchUserException, TeacherHasEventsException {
        needsToUpdateInMemoryData();
        needsUpdate();
    }

    @Override
    public void deleteEvent(String eventId) throws SQLException, NoSuchEventException {
        needsToUpdateInMemoryData();
        needsUpdate();
    }

    @Override
    public List<Event> getAllEvents() throws SQLException, InconsistentDatabaseException {
        List<Event> result = new ArrayList<Event>();
        result.addAll(eventById.values());
        return result;
    }

    @Override
    public Event getEventById(String eventId) throws SQLException {
        return eventById.get(eventId);
    }

    @Override
    public void modifySchool(EditSchoolFormData school) throws SQLException, NoSuchSchoolException {
        needsToUpdateInMemoryData();
        needsUpdate();
    }

    @Override
    public void insertNewBankAndAdmin(CreateBankFormData formData) throws SQLException, EmailAlreadyInUseException {
        needsToUpdateInMemoryData();
        needsUpdate();
    }

    @Override
    public void modifyBankAndBankAdmin(EditBankFormData formData) throws SQLException, EmailAlreadyInUseException, NoSuchBankException {
        needsToUpdateInMemoryData();
        needsUpdate();
    }

    @Override
    public void setBankSpecificFieldLabel(SetBankSpecificFieldLabelFormData formData) throws SQLException, NoSuchBankException {
        needsToUpdateInMemoryData();
        needsUpdate();
    }

    @Override
    public void insertNewSchool(CreateSchoolFormData school) throws SQLException {
        needsToUpdateInMemoryData();
        needsUpdate();
    }

    @Override
    public void insertNewAllowedDate(AddAllowedDateFormData formData) throws SQLException, AllowedDateAlreadyInUseException {
        needsToUpdateInMemoryData();
        needsUpdate();
    }

    @Override
    public void insertNewAllowedTime(AddAllowedTimeFormData formData) throws SQLException, AllowedTimeAlreadyInUseException, NoSuchAllowedTimeException {
        needsToUpdateInMemoryData();
        needsUpdate();
    }

    @Override
    public void modifyEvent(EventRegistrationFormData formData) throws SQLException, NoSuchEventException {
        needsToUpdateInMemoryData();
        needsUpdate();
    }

    @Override
    public void updateUserCredential(String userId, String hashedPassword, String salt) throws SQLException {
        needsToUpdateInMemoryData();
        needsUpdate();
    }

    @Override
    public void updateResetPasswordToken(String userId, String resetPasswordToken) throws SQLException {
        needsToUpdateInMemoryData();
        needsUpdate();
    }

    @Override
    public void updateUserStatusById(String userId, int userStatus) throws SQLException {
        needsToUpdateInMemoryData();
        needsUpdate();
    }

    @Override
    public void deleteAllowedTime(String time) throws SQLException, NoSuchAllowedTimeException {
        needsToUpdateInMemoryData();
        needsUpdate();
    }

    @Override
    public void deleteAllowedDate(PrettyPrintingDate date) throws SQLException, NoSuchAllowedDateException {
        needsToUpdateInMemoryData();
        needsUpdate();
    }

    @Override
    public SiteStatistics getSiteStatistics() throws SQLException {
        int numEvents = 0;
        int numMatchedEvents = 0;
        int numUnmatchedEvents = 0;
        int num3rdGradeEvents = 0;
        int num4thGradeEvents = 0;
        Set<Teacher> participatingTeachers = new HashSet<Teacher>();
        Set<School> participatingSchools = new HashSet<School>();
        for (Event event : eventById.values()) {
            numEvents += 1;
            if (event.getVolunteerId() == null) {
                numUnmatchedEvents += 1;
            }
            else {
                numMatchedEvents += 1;
            }
            if (event.getGrade().equals("3")) {
                num3rdGradeEvents += 1;
            }
            else if (event.getGrade().equals("4")) {
                num4thGradeEvents += 1;
            }
            participatingTeachers.add(event.getLinkedTeacher());
            participatingSchools.add(event.getLinkedTeacher().getLinkedSchool());
        }
        SiteStatistics result = new SiteStatistics();
        result.setNumEvents(numEvents);
        result.setNumMatchedEvents(numMatchedEvents);
        result.setNumUnmatchedEvents(numUnmatchedEvents);
        result.setNum3rdGradeEvents(num3rdGradeEvents);
        result.setNum4thGradeEvents(num4thGradeEvents);
        result.setNumVolunteers(matchedVolunteers.size() + unmatchedVolunteers.size());
        result.setNumParticipatingTeachers(participatingTeachers.size());
        result.setNumParticipatingSchools(participatingSchools.size());
        return result;
    }

    @Override
    public List<Teacher> getTeacherWithSchoolData() throws SQLException {

        xxx;
    }

    @Override
    public List<Teacher> getTeachersBySchool(String schoolId) throws SQLException {
        xxx;
    }

    @Override
    public List<Volunteer> getVolunteersWithBankData() throws SQLException {
        List<Volunteer> allVolunteers = new ArrayList<Volunteer>();
        allVolunteers.addAll(unmatchedVolunteers);
        allVolunteers.addAll(matchedVolunteers);
        return allVolunteers;
    }

    @Override
    public List<Teacher> getMatchedTeachers() throws SQLException {
        return Collections.unmodifiableList(matchedTeachers);
    }

    @Override
    public List<Teacher> getUnMatchedTeachers() throws SQLException {
        return Collections.unmodifiableList(unmatchedTeachers);
    }

    @Override
    public List<Volunteer> getMatchedVolunteers() throws SQLException {
        return Collections.unmodifiableList(matchedVolunteers);
    }

    @Override
    public List<Volunteer> getUnMatchedVolunteers() throws SQLException {
        return Collections.unmodifiableList(unmatchedVolunteers);
    }

    @Override
    public List<BankAdmin> getBankAdmins() throws SQLException {
        return Collections.unmodifiableList(bankAdmins);
    }

    @Override
    public Map<String, String> getSiteSettings() throws SQLException {
        return Collections.unmodifiableMap(siteSettings);
    }

    @Override
    public void modifySiteSetting(String settingName, String settingValue) throws SQLException {
        needsToUpdateInMemoryData();
        needsUpdate();
    }
}
