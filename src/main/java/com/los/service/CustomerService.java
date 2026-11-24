package com.los.service;

import com.los.config.AppConfig;
import com.los.config.DatabaseConfig;
import com.los.dao.CustomerDAO;
import com.los.model.Customer;
import com.los.model.DataAccessException;
import com.los.model.DuplicateEntityException;
import com.los.model.EntityNotFoundException;
import com.los.model.ValidationException;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service orchestrating Customer lifecycle operations.
 */
public class CustomerService {

    private static final Pattern MOBILE_PATTERN = Pattern.compile("^[0-9]{10}$");

    private final CustomerDAO customerDAO;
    private final IdGeneratorService idGeneratorService;
    private final AppConfig config;

    public CustomerService() {
        this.customerDAO = new CustomerDAO();
        this.idGeneratorService = new IdGeneratorService();
        this.config = AppConfig.getInstance();
    }

    public CustomerService(CustomerDAO customerDAO, IdGeneratorService idGeneratorService, AppConfig config) {
        this.customerDAO = customerDAO;
        this.idGeneratorService = idGeneratorService;
        this.config = config;
    }

    /**
     * Registers a new customer with validation and collision-checked ID generation.
     */
    public Customer registerCustomer(Customer customer) {
        validateCustomerInput(customer);

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (customerDAO.existsByMobile(conn, customer.getMobile())) {
                    throw new DuplicateEntityException("A customer with mobile number [" + customer.getMobile() + "] already exists.");
                }

                String generatedId = idGeneratorService.generateCustomerId(conn);
                customer.setCustomerId(generatedId);
                customer.setCreatedAt(OffsetDateTime.now());

                customerDAO.insert(conn, customer);
                conn.commit();
                return customer;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (DuplicateEntityException | ValidationException e) {
            throw e;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to register customer: " + e.getMessage(), e);
        }
    }

    public Customer getCustomerById(String customerId) {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new ValidationException("Customer ID cannot be empty.");
        }
        try (Connection conn = DatabaseConfig.getConnection()) {
            return customerDAO.findById(conn, customerId.trim())
                    .orElseThrow(() -> new EntityNotFoundException("Customer not found with ID: " + customerId));
        } catch (SQLException e) {
            throw new DataAccessException("Error fetching customer by ID: " + e.getMessage(), e);
        }
    }

    public Customer getCustomerByMobile(String mobile) {
        if (mobile == null || !MOBILE_PATTERN.matcher(mobile.trim()).matches()) {
            throw new ValidationException("Invalid 10-digit mobile number: " + mobile);
        }
        try (Connection conn = DatabaseConfig.getConnection()) {
            return customerDAO.findByMobile(conn, mobile.trim())
                    .orElseThrow(() -> new EntityNotFoundException("Customer not found with mobile: " + mobile));
        } catch (SQLException e) {
            throw new DataAccessException("Error fetching customer by mobile: " + e.getMessage(), e);
        }
    }

    public List<Customer> getAllCustomers() {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return customerDAO.findAll(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Error retrieving customer list: " + e.getMessage(), e);
        }
    }

    private void validateCustomerInput(Customer customer) {
        List<String> errors = new ArrayList<>();
        if (customer == null) {
            throw new ValidationException("Customer object cannot be null.");
        }
        if (customer.getName() == null || customer.getName().trim().isEmpty()) {
            errors.add("Customer name is required.");
        }
        if (customer.getMobile() == null || !MOBILE_PATTERN.matcher(customer.getMobile().trim()).matches()) {
            errors.add("Valid 10-digit mobile number is required.");
        }
        if (customer.getDateOfBirth() == null) {
            errors.add("Date of birth is required.");
        } else {
            LocalDate bDate = config.getBusinessDate();
            if (customer.getDateOfBirth().isAfter(bDate)) {
                errors.add("Date of birth cannot be in the future.");
            }
        }
        if (customer.getAddress() == null || customer.getAddress().trim().isEmpty()) {
            errors.add("Customer address is required.");
        }
        if (customer.getKycDetails() == null || customer.getKycDetails().trim().isEmpty()) {
            errors.add("KYC details are required.");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }
}
