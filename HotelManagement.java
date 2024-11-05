package com.akshay.Project;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

class HotelManagementException extends Exception {
    public HotelManagementException(String message) {
        super(message);
    }
}
class InvalidCheckoutDateException extends HotelManagementException {
    public InvalidCheckoutDateException(String message) {
        super(message);
    }
}

public class HotelManagement extends JFrame {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/hotel_db";
    private static final String USER = "root";
    private static final String PASSWORD = "root";
    private static final double DAILY_RATE = 400.0;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private JTextField guestNameField;
    private JTextField contactNumberField;
    private JTextField checkoutDateField;
    private JTextField reservationIdField;

    public HotelManagement() {
        setTitle("Hotel Management System");
        setSize(400, 350);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(7, 2));

        guestNameField = new JTextField();
        contactNumberField = new JTextField();
        checkoutDateField = new JTextField();
        reservationIdField = new JTextField();

        JButton reserveButton = new JButton("Reserve Room");
        JButton viewButton = new JButton("View Reservations");
        JButton vacateButton = new JButton("Check Vacating Guests");
        JButton deleteButton = new JButton("Delete Reservation");

        reserveButton.addActionListener(new ReserveRoomAction());
        viewButton.addActionListener(e -> viewReservations());
        vacateButton.addActionListener(e -> checkVacatingGuests());
        deleteButton.addActionListener(e -> deleteReservation());

        add(new JLabel("Guest Name:"));
        add(guestNameField);
        add(new JLabel("Contact Number:"));
        add(contactNumberField);
        add(new JLabel("Checkout Date (YYYY-MM-DD):"));
        add(checkoutDateField);
        add(new JLabel("Reservation ID (to delete):"));
        add(reservationIdField);
        add(reserveButton);
        add(viewButton);
        add(vacateButton);
        add(deleteButton);
    }

    // Inner class for handling the room reservation action
    private class ReserveRoomAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            reserveRoom();
        }
    }

    private void reserveRoom() {
        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            // Check for available rooms
            String sql = "SELECT room_number FROM reservations";
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(sql);

            boolean[] roomOccupied = new boolean[5];
            while (resultSet.next()) {
                int reservedRoom = resultSet.getInt("room_number");
                if (reservedRoom >= 1 && reservedRoom <= 5) {
                    roomOccupied[reservedRoom - 1] = true;
                }
            }

            int assignedRoom = -1;
            for (int i = 0; i < roomOccupied.length; i++) {
                if (!roomOccupied[i]) {
                    assignedRoom = i + 1;
                    break;
                }
            }

            if (assignedRoom == -1) {
                throw new HotelManagementException("No rooms available for reservation.");
            }

            String guestName = guestNameField.getText();
            String contactNumber = contactNumberField.getText();

            String checkoutDateInput = checkoutDateField.getText();
            if (checkoutDateInput.isEmpty()) {
                throw new InvalidCheckoutDateException("Checkout date cannot be empty.");
            }

            LocalDate checkoutDate;
            try {
                checkoutDate = LocalDate.parse(checkoutDateInput, DATE_FORMAT);
            } catch (DateTimeParseException e) {
                throw new HotelManagementException("Invalid date format. Please enter the date in YYYY-MM-DD format.");
            }

            LocalDate currentDate = LocalDate.now();
            if (checkoutDate.isBefore(currentDate)) {
                throw new InvalidCheckoutDateException("Checkout date cannot be before today's date.");
            }
            long daysBetween = ChronoUnit.DAYS.between(currentDate, checkoutDate);
            double totalCost = daysBetween * DAILY_RATE;
            JOptionPane.showMessageDialog(this, "Reservation Cost for " + daysBetween + " days: $" + totalCost);

            String insertSql = "INSERT INTO reservations (guest_name, room_number, contact_number, checkout_date) " +
                    "VALUES (?, ?, ?, ?)";
            PreparedStatement preparedStatement = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            preparedStatement.setString(1, guestName);
            preparedStatement.setInt(2, assignedRoom);
            preparedStatement.setString(3, contactNumber);
            preparedStatement.setString(4, checkoutDate.toString());
            int affectedRows = preparedStatement.executeUpdate();

            if (affectedRows > 0) {
                ResultSet generatedKeys = preparedStatement.getGeneratedKeys();
                if (generatedKeys.next()) {
                    int reservationId = generatedKeys.getInt(1);
                    JOptionPane.showMessageDialog(this, "Reservation successful! Assigned Room: " + assignedRoom + ", Reservation ID: " + reservationId);
                }
            } else {
                throw new HotelManagementException("Reservation failed.");
            }
        } catch (InvalidCheckoutDateException e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
        } catch (HotelManagementException e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Database error: " + e.getMessage());
        }
    }

    private void viewReservations() {
        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            String sql = "SELECT * FROM reservations";
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(sql);

            StringBuilder reservations = new StringBuilder();
            while (resultSet.next()) {
                int reservationId = resultSet.getInt("reservation_id");
                String guestName = resultSet.getString("guest_name");
                int roomNumber = resultSet.getInt("room_number");
                String contactNumber = resultSet.getString("contact_number");
                String reservationDate = resultSet.getTimestamp("reservation_date").toString();
                String checkoutDate = resultSet.getDate("checkout_date").toString();

                reservations.append(String.format("ID: %d, Name: %s, Room: %d, Contact: %s, Reserved on: %s, Checkout: %s\n",
                        reservationId, guestName, roomNumber, contactNumber, reservationDate, checkoutDate));
            }
            if (reservations.length() == 0) {
                reservations.append("No current reservations.");
            } else {
                reservations.insert(0, "Current Reservations:\n");
            }
            JOptionPane.showMessageDialog(this, reservations.toString());
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Database error: " + e.getMessage());
        }
    }

    private void checkVacatingGuests() {
        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            String sql = "SELECT guest_name, room_number, contact_number, checkout_date FROM reservations " +
                    "WHERE checkout_date <= CURDATE()";
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(sql);

            StringBuilder vacatingGuests = new StringBuilder("Guests who are due to vacate:\n");
            while (resultSet.next()) {
                String guestName = resultSet.getString("guest_name");
                int roomNumber = resultSet.getInt("room_number");
                String contactNumber = resultSet.getString("contact_number");
                String checkoutDate = resultSet.getDate("checkout_date").toString();

                vacatingGuests.append(String.format("Guest: %s, Room: %d, Contact: %s, Checkout: %s\n",
                        guestName, roomNumber, contactNumber, checkoutDate));
            }

            JOptionPane.showMessageDialog(this, vacatingGuests.toString());
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Database error: " + e.getMessage());
        }
    }

    private void deleteReservation() {
        String reservationIdInput = reservationIdField.getText();
        if (reservationIdInput.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a reservation ID to delete.");
            return;
        }

        int reservationId;
        try {
            reservationId = Integer.parseInt(reservationIdInput);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid reservation ID. Please enter a numeric value.");
            return;
        }

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            String deleteSql = "DELETE FROM reservations WHERE reservation_id = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(deleteSql);
            preparedStatement.setInt(1, reservationId);
            int affectedRows = preparedStatement.executeUpdate();

            if (affectedRows > 0) {
                JOptionPane.showMessageDialog(this, "Reservation with ID " + reservationId + " deleted successfully.");
            } else {
                throw new HotelManagementException("No reservation found with ID " + reservationId + ".");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Database error: " + e.getMessage());
        } catch (HotelManagementException e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            HotelManagement HM = new HotelManagement();
            HM.setVisible(true);
        });
    }
}
