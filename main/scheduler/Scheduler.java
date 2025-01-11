package scheduler;

import scheduler.db.ConnectionManager;
import scheduler.model.Caregiver;
import scheduler.model.Patient;
import scheduler.model.Vaccine;
import scheduler.util.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;

public class Scheduler {

    // objects to keep track of the currently logged-in user
    // Note: it is always true that at most one of currentCaregiver and currentPatient is not null
    //       since only one user can be logged-in at a time
    private static Caregiver currentCaregiver = null;
    private static Patient currentPatient = null;

    public static void main(String[] args) {
        // printing greetings text
        System.out.println();
        System.out.println("Welcome to the COVID-19 Vaccine Reservation Scheduling Application!");
        System.out.println("*** Please enter one of the following commands ***");
        System.out.println("> create_patient <username> <password>");  
        System.out.println("> create_caregiver <username> <password>");
        System.out.println("> login_patient <username> <password>");  
        System.out.println("> login_caregiver <username> <password>");
        System.out.println("> search_caregiver_schedule <date>");  
        System.out.println("> reserve <date> <vaccine>"); 
        System.out.println("> upload_availability <date>");
        System.out.println("> cancel <appointment_id>");  
        System.out.println("> add_doses <vaccine> <number>");
        System.out.println("> show_appointments"); 
        System.out.println("> logout");  
        System.out.println("> quit");
        System.out.println();

        // read input from user
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("> ");
            String response = "";
            try {
                response = r.readLine();
            } catch (IOException e) {
                System.out.println("Please try again!");
            }
            // split the user input by spaces
            String[] tokens = response.split(" ");
            // check if input exists
            if (tokens.length == 0) {
                System.out.println("Please try again!");
                continue;
            }
            // determine which operation to perform
            String operation = tokens[0];
            if (operation.equals("create_patient")) {
                createPatient(tokens);
            } else if (operation.equals("create_caregiver")) {
                createCaregiver(tokens);
            } else if (operation.equals("login_patient")) {
                loginPatient(tokens);
            } else if (operation.equals("login_caregiver")) {
                loginCaregiver(tokens);
            } else if (operation.equals("search_caregiver_schedule")) {
                searchCaregiverSchedule(tokens);
            } else if (operation.equals("reserve")) {
                reserve(tokens);
            } else if (operation.equals("upload_availability")) {
                uploadAvailability(tokens);
            } else if (operation.equals("cancel")) {
                cancel(tokens);
            } else if (operation.equals("add_doses")) {
                addDoses(tokens);
            } else if (operation.equals("show_appointments")) {
                showAppointments(tokens);
            } else if (operation.equals("logout")) {
                logout(tokens);
            } else if (operation.equals("quit")) {
                System.out.println("Bye!");
                return;
            } else {
                System.out.println("Invalid operation name!");
            }
        }
    }

    private static void createPatient(String[] tokens) {
        // create_patient <username> <password>
        // check 1: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Create patient failed");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];

        // check 2: check if the username is unique
        if(usernameExistsPatient(username)) {
            System.out.println("Username taken, try again");
            return;
        }

        byte[] salt = Util.generateSalt();
        byte[] hash = Util.generateHash(password, salt);

        try {
            Patient patient = new Patient.PatientBuilder(username, salt, hash).build();
            patient.saveToDB();
            System.out.println("Created user " + username);
        } catch (SQLException e) {
            System.out.println("Create patient failed");
            e.printStackTrace();
        }
    }

    private static boolean usernameExistsPatient(String username) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String selectUsername = "SELECT * FROM Patients WHERE Username = ?";
        try {
            PreparedStatement statement = con.prepareStatement(selectUsername);
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
            // returns false if the cursor is not before the first record or if there are no rows in the ResultSet.
            return resultSet.isBeforeFirst();
        } catch (SQLException e) {
            System.out.println("Error occurred when checking username");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return true;
    }

    private static void createCaregiver(String[] tokens) {
        // create_caregiver <username> <password>
        // check 1: the length for tokens need to be exactly 3 to include all information (with operation name)
        if (tokens.length != 3) {
            System.out.println("Failed to create user.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];
        // check 2: check if the username has been taken already
        if (usernameExistsCaregiver(username)) {
            System.out.println("Username taken, try again!");
            return;
        }
        byte[] salt = Util.generateSalt();
        byte[] hash = Util.generateHash(password, salt);
        // create the caregiver
        try {
            Caregiver caregiver = new Caregiver.CaregiverBuilder(username, salt, hash).build(); 
            // save to caregiver information to our database
            caregiver.saveToDB();
            System.out.println("Created user " + username);
        } catch (SQLException e) {
            System.out.println("Failed to create user.");
            e.printStackTrace();
        }
    }

    private static boolean usernameExistsCaregiver(String username) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String selectUsername = "SELECT * FROM Caregivers WHERE Username = ?";
        try {
            PreparedStatement statement = con.prepareStatement(selectUsername);
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
            // returns false if the cursor is not before the first record or if there are no rows in the ResultSet.
            return resultSet.isBeforeFirst();
        } catch (SQLException e) {
            System.out.println("Error occurred when checking username");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return true;
    }

    private static void loginPatient(String[] tokens) {
        // login_caregiver <username> <password>
        // check 1: if someone's already logged-in, they need to log out first
        if (currentCaregiver != null || currentPatient != null) {
            System.out.println("User already logged in, try again");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Login patient failed");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];

        Patient patient = null;
        try {
            patient = new Patient.PatientGetter(username, password).get();
        } catch (SQLException e) {
            System.out.println("Login patient failed");
            e.printStackTrace();
        }
        // check if the login was successful
        if (patient == null) {
            System.out.println("Login patient failed");
        } else {
            System.out.println("Logged in as " + username);
            currentPatient = patient;
        }
    }

    private static void loginCaregiver(String[] tokens) {
        // login_caregiver <username> <password>
        // check 1: if someone's already logged-in, they need to log out first
        if (currentCaregiver != null || currentPatient != null) {
            System.out.println("User already logged in.");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Login failed.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];

        Caregiver caregiver = null;
        try {
            caregiver = new Caregiver.CaregiverGetter(username, password).get();
        } catch (SQLException e) {
            System.out.println("Login failed.");
            e.printStackTrace();
        }
        // check if the login was successful
        if (caregiver == null) {
            System.out.println("Login failed.");
        } else {
            System.out.println("Logged in as: " + username);
            currentCaregiver = caregiver;
        }
    }

    // This method searches and prints the availability of a caregiver on a specific day and the
    // number of available doses. The output will print the available caregivers first and then
    // the number of available doses.
    private static void searchCaregiverSchedule(String[] tokens) {
        // search_caregiver_schedule <date>
        // check 1: check if user is logged in the first place
        if(currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first");
            return;
        } else if(tokens.length != 2) {
            // check 2: check if there are 2 tokens
            System.out.println("Please try again");
            return;
        }

        // get the date and print out stuff
        String date = tokens[1];
        Date d = null;
        try {
            d = Date.valueOf(date);
        } catch (IllegalArgumentException e) {
            System.out.println("Please try again");
            return;
        }

        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        // print out caregivers first
        String checkSched = "SELECT C.Username FROM Caregivers C, Availabilities A WHERE A.Username = C.Username AND Time = ? ORDER BY C.Username";
        try {
            PreparedStatement statement = con.prepareStatement(checkSched);
            statement.setDate(1, d);
            ResultSet resultSet = statement.executeQuery();

            while(resultSet.next()) {
                System.out.println(resultSet.getString("Username"));
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Please try again");
        } catch (SQLException e) {
            System.out.println("Error occurred when searching for caregiver");
            e.printStackTrace();
            return;
        }

        // then vaccines
        String checkVacc = "SELECT * FROM Vaccines ORDER BY Vaccines.Name";
        try {
            PreparedStatement statement = con.prepareStatement(checkVacc);
            ResultSet resultSet = statement.executeQuery();

            while(resultSet.next()) {
                String vacc = resultSet.getString("Name");
                int numOfVacc = resultSet.getInt("Doses");
                System.out.println(vacc + " " + numOfVacc);
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Please try again");
        } catch (SQLException e) {
            System.out.println("Error occurred when searching for vaccine");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
    }

    // This method reserves a caregiver to administer the dose for the patient. Only patients
    // can reserve, and if there are no caregiver to administer or any remaining doses then
    // the reservation will not go through.
    private static void reserve(String[] tokens) {
        // reserve <date> <vaccine>
        if(currentCaregiver == null && currentPatient == null) {
            // check 1: check if user is logged in the first place
            System.out.println("Please login first");
            return;
        } else if(currentCaregiver != null) {
            // check 2: check if user is a patient
            System.out.println("Please login as a patient");
            return;
        } else if(tokens.length != 3) {
            // check 3: check if there's 3 tokens
            System.out.println("Please try again");
            return;
        }

        Date d = null;
        // check if it's valid date
        try {
            d = Date.valueOf(tokens[1]);
        } catch (IllegalArgumentException e) {
            System.out.println("Please try again");
            return;
        }
        String vaccine = tokens[2];

        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        // check vaccine availability
        String checkVacc = "SELECT V.doses FROM Vaccines V WHERE V.Name = ?";
        int dose = 0;
        try {
            PreparedStatement statement = con.prepareStatement(checkVacc);
            statement.setString(1, vaccine);
            ResultSet resultSet = statement.executeQuery();

            if(!resultSet.next()) {
                // if resultSet doesn't have vaccine
                System.out.println("Please try again");
                cm.closeConnection();
                return;
            } else if(resultSet.getInt("Doses") <= 0) {
                // if resultSet has vaccine but no doses
                System.out.println("Not enough available doses");
                cm.closeConnection();
                return;
            }

            dose = resultSet.getInt("Doses");
        } catch (IllegalArgumentException e) {
            System.out.println("Please try again");
        } catch (SQLException e) {
            System.out.println("Error occurred when searching for vaccine");
            e.printStackTrace();
        }

        // check caregiver availability, ordering alphabetically
        String checkCaregiver = "SELECT A.Username FROM Availabilities A WHERE Time = ? ORDER BY A.Username";
        String caregiver = null;
        try {
            PreparedStatement statement = con.prepareStatement(checkCaregiver);
            statement.setDate(1, d);
            ResultSet resultSet = statement.executeQuery();

            if(!resultSet.next()) {
                // if resultSet doesn't have caregiver
                System.out.println("No caregiver is available");
                cm.closeConnection();
                return;
            }

            caregiver = resultSet.getString("Username");
        } catch (IllegalArgumentException e) {
            System.out.println("Please try again");
        } catch (SQLException e) {
            System.out.println("Error occurred when searching for caregiver");
            e.printStackTrace();
        }

        // creating a new appointment id
        String getPrevAid = "SELECT MAX(aid) AS max FROM Appointments";
        int currAid = 0;
        try {
            PreparedStatement statement = con.prepareStatement(getPrevAid);
            ResultSet resultSet = statement.executeQuery();

            if(resultSet.next()) {
                currAid = resultSet.getInt("max") + 1;
            } else {
                currAid = 1;
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Please try again");
        } catch (SQLException e) {
            System.out.println("Error occurred when searching for aid");
            e.printStackTrace();
        }

        // update appointment database between patient and caregiver
        String appointment = "INSERT INTO Appointments VALUES (?, ?, ?, ?, ?)";
        try {
            PreparedStatement statement = con.prepareStatement(appointment);
            statement.setInt(1, currAid);
            statement.setString(2, vaccine);
            statement.setString(3, currentPatient.getUsername());
            statement.setString(4, caregiver);
            statement.setDate(5, d);
            statement.executeUpdate();
            con.commit();
        } catch (IllegalArgumentException e) {
            System.out.println("Please try again");
        } catch (SQLException e) {
            System.out.println("Error occurred when updating appointment");
            e.printStackTrace();
            return;
        }

        // update availability where caregiver isn't available on the day anymore
        String availability = "DELETE FROM Availabilities WHERE Username = ? AND Time = ?";
        try {
            PreparedStatement statement = con.prepareStatement(availability);
            statement.setString(1, caregiver);
            statement.setDate(2, d);
            statement.executeUpdate();
            con.commit();
        } catch (IllegalArgumentException e) {
            System.out.println("Please try again");
        } catch (SQLException e) {
            System.out.println("Error occurred when updating availability");
            e.printStackTrace();
            return;
        } finally {
            cm.closeConnection();
        }

        // update doses to go down by 1
        Vaccine updateDoses = null;
        try {
            updateDoses = new Vaccine.VaccineGetter(vaccine).get();
            updateDoses.decreaseAvailableDoses(1);
        } catch (SQLException e) {
            System.out.println("Error occurred when updating vaccine");
            e.printStackTrace();
            return;
        }

        System.out.println("Appointment ID " + currAid + ", Caregiver username " + caregiver);
        // This one was rough x.x
    }

    private static void uploadAvailability(String[] tokens) {
        // upload_availability <date>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 2 to include all information (with the operation name)
        if (tokens.length != 2) {
            System.out.println("Please try again!");
            return;
        }
        String date = tokens[1];

        try {
            Date d = Date.valueOf(date);
            currentCaregiver.uploadAvailability(d);
            System.out.println("Availability uploaded!");
        } catch (IllegalArgumentException e) {
            System.out.println("Please enter a valid date!");
        } catch (SQLException e) {
            System.out.println("Error occurred when uploading availability");
            e.printStackTrace();
            return;
        }
    }

    private static void cancel(String[] tokens) {
        // cancel <appointment_id>
        if(currentCaregiver == null && currentPatient == null) {
            // check 1: check if user is logged in the first place
            System.out.println("Please login first");
            return;
        } else if(tokens.length != 2) {
            // check 2: check if there's 2 tokens
            System.out.println("Please try again");
            return;
        }
        String aid = tokens[1];

        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        Date d = null;
        String caregiver = null;
        Vaccine vaccine = null;

        // get the appointment info to delete appointment, update caretaker availability, and doses
        String apptInfo = "SELECT Time, caregiver_name, vaccine_name FROM Appointments WHERE aid = ?";
        try {
            PreparedStatement statement = con.prepareStatement(apptInfo);
            statement.setString(1, aid);
            ResultSet resultSet = statement.executeQuery();

            // If there's no vaccine, time, or caregiver of that appointment
            if(!resultSet.next()) {
                // if resultSet doesn't have caregiver
                System.out.println("There's no appointment with the ID: " + aid);
                cm.closeConnection();
                return;
            }

            d = resultSet.getDate(1);
            caregiver = resultSet.getString(2);
            vaccine = new Vaccine.VaccineGetter(resultSet.getString(3)).get();
        } catch (SQLException e) {
            System.out.println("Error occurred when getting appointment information");
            e.printStackTrace();
            return;
        }

        // delete the appointment with the aid
        String apptDeleted = "DELETE FROM Appointments WHERE aid = ?";
        try {
            PreparedStatement statement = con.prepareStatement(apptDeleted);
            statement.setString(1, aid);
            statement.executeUpdate();
            con.commit();
        } catch (SQLException e) {
            System.out.println("Error occurred when getting appointment information");
            e.printStackTrace();
            return;
        }

        // update caregiver's availability on that day
        String apptAvail = "INSERT INTO Availabilities VALUES (?, ?)";
        try {
            PreparedStatement statement = con.prepareStatement(apptAvail);
            statement.setDate(1, d);
            statement.setString(2, caregiver);
            statement.executeUpdate();
            con.commit();
        } catch (SQLException e) {
            System.out.println("Error occurred when updating availability");
            e.printStackTrace();
            return;
        }

        // increase the amount of doses by 1
        try {
            vaccine.increaseAvailableDoses(1);
        } catch (SQLException e) {
            System.out.println("Error occurred when decreasing doses");
        } finally {
            cm.closeConnection();
        }

        System.out.println("Appointment successfully cancelled");
        // PREACHHH THIS WORKS!
    }

    private static void addDoses(String[] tokens) {
        // add_doses <vaccine> <number>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }
        String vaccineName = tokens[1];
        int doses = Integer.parseInt(tokens[2]);
        Vaccine vaccine = null;
        try {
            vaccine = new Vaccine.VaccineGetter(vaccineName).get();
        } catch (SQLException e) {
            System.out.println("Error occurred when adding doses");
            e.printStackTrace();
        }
        // check 3: if getter returns null, it means that we need to create the vaccine and insert it into the Vaccines
        //          table
        if (vaccine == null) {
            try {
                vaccine = new Vaccine.VaccineBuilder(vaccineName, doses).build();
                vaccine.saveToDB();
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        } else {
            // if the vaccine is not null, meaning that the vaccine already exists in our table
            try {
                vaccine.increaseAvailableDoses(doses);
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        }
        System.out.println("Doses updated!");
    }

    // This method shows appointments, and depending on who is logged in, a certain format would
    // be printed.
    private static void showAppointments(String[] tokens) {
        // show_appointments
        if(currentCaregiver == null && currentPatient == null) {
            // check 1: check if user is logged in the first place
            System.out.println("Please login first");
            return;
        } else if(tokens.length != 1) {
            // check 2: check if there's 1 token
            System.out.println("Please try again");
            return;
        }

        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        if (currentCaregiver != null) {
            // if the person logged in is a caregiver, then the following format would be printed:
            // <doses> <vaccine> <date> <patient>
            String info = "SELECT aid, vaccine_name, Time, patient_name FROM Appointments WHERE caregiver_name = ? ORDER BY aid";
            try {
                PreparedStatement statement = con.prepareStatement(info);
                statement.setString(1, currentCaregiver.getUsername());
                ResultSet resultSet = statement.executeQuery();
                //
                while(resultSet.next()) {
                    String print = resultSet.getInt(1) + " " + resultSet.getString(2) + " " +
                            resultSet.getDate(3) + " " + resultSet.getString(4);
                    System.out.println(print);
                }
                cm.closeConnection();
            } catch (SQLException e) {
                System.out.println("Error occurred when searching for caregiver");
            }
        } else {
            // if the person logged in is a patient, then the following format would be printed:
            // <doses> <vaccine> <date> <caregiver>
            String info = "SELECT aid, vaccine_name, Time, caregiver_name FROM Appointments WHERE patient_name = ? ORDER BY aid";
            try {
                PreparedStatement statement = con.prepareStatement(info);
                statement.setString(1, currentPatient.getUsername());
                ResultSet resultSet = statement.executeQuery();
                //
                while(resultSet.next()) {
                    String print = resultSet.getInt(1) + " " + resultSet.getString(2) + " " +
                            resultSet.getDate(3) + " " + resultSet.getString(4);
                    System.out.println(print);
                }
                cm.closeConnection();
            } catch (SQLException e) {
                System.out.println("Error occurred when searching for patient");
            }
        }
    }

    // This method logs out the user
    private static void logout(String[] tokens) {
        // logout
        if(currentCaregiver == null && currentPatient == null) {
            // check 1: check if user is logged-in
            System.out.println("Please login first");
            return;
        } else if (tokens.length != 1) {
            // check 2: check if there's 1 token
            System.out.println("Please try again");
            return;
        }

        // update to make either values null depending on who is logged in
        if(currentCaregiver != null) {
            currentCaregiver = null;
        } else {
            currentPatient = null;
        }

        System.out.println("Successfully logged out");
    }
}
