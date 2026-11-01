package com.codealpha.buspass.dto;

import java.time.LocalDate;

import com.codealpha.buspass.model.PassengerCategory;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class BookTicketRequest {

    @NotBlank(message = "Passenger name is required")
    @Size(max = 120, message = "Passenger name is too long")
    private String passengerName;

    @NotBlank(message = "Email is required")
    @Size(max = 150, message = "Email is too long")
    @Email(message = "Email format is invalid")
    private String email;

    @NotBlank(message = "Phone is required")
    @Size(min = 7, max = 20, message = "Phone length must be between 7 and 20 characters")
    @Pattern(regexp = "[+0-9 ()-]*", message = "Phone may only contain digits, spaces and + ( ) -")
    private String phone;

    @NotBlank(message = "Route code is required")
    private String routeCode;

    @NotNull(message = "Travel date is required")
    @FutureOrPresent(message = "Travel date cannot be in the past")
    private LocalDate travelDate;

    @NotNull(message = "Passenger category is required")
    private PassengerCategory passengerCategory;

    public String getPassengerName() {
        return passengerName;
    }

    public void setPassengerName(String passengerName) {
        this.passengerName = passengerName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRouteCode() {
        return routeCode;
    }

    public void setRouteCode(String routeCode) {
        this.routeCode = routeCode;
    }

    public LocalDate getTravelDate() {
        return travelDate;
    }

    public void setTravelDate(LocalDate travelDate) {
        this.travelDate = travelDate;
    }

    public PassengerCategory getPassengerCategory() {
        return passengerCategory;
    }

    public void setPassengerCategory(PassengerCategory passengerCategory) {
        this.passengerCategory = passengerCategory;
    }
}
