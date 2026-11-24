package com.los.dao;

import com.los.model.Customer;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Raw JDBC DAO for Customer management.
 */
public class CustomerDAO {

    public void insert(Connection conn, Customer customer) throws SQLException {
        String sql = """
            INSERT INTO customer (customer_id, name, date_of_birth, mobile, address, kyc_details, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, customer.getCustomerId());
            ps.setString(2, customer.getName());
            ps.setDate(3, Date.valueOf(customer.getDateOfBirth()));
            ps.setString(4, customer.getMobile());
            ps.setString(5, customer.getAddress());
            ps.setString(6, customer.getKycDetails());
            OffsetDateTime createdAt = customer.getCreatedAt() != null ? customer.getCreatedAt() : OffsetDateTime.now();
            ps.setTimestamp(7, Timestamp.valueOf(createdAt.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()));
            ps.executeUpdate();
        }
    }

    public Optional<Customer> findById(Connection conn, String customerId) throws SQLException {
        String sql = "SELECT customer_id, name, date_of_birth, mobile, address, kyc_details, created_at FROM customer WHERE customer_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Customer> findByMobile(Connection conn, String mobile) throws SQLException {
        String sql = "SELECT customer_id, name, date_of_birth, mobile, address, kyc_details, created_at FROM customer WHERE mobile = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, mobile);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        }
        return Optional.empty();
    }

    public boolean existsById(Connection conn, String customerId) throws SQLException {
        String sql = "SELECT 1 FROM customer WHERE customer_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean existsByMobile(Connection conn, String mobile) throws SQLException {
        String sql = "SELECT 1 FROM customer WHERE mobile = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, mobile);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<Customer> findAll(Connection conn) throws SQLException {
        String sql = "SELECT customer_id, name, date_of_birth, mobile, address, kyc_details, created_at FROM customer ORDER BY created_at DESC";
        List<Customer> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    private Customer mapResultSet(ResultSet rs) throws SQLException {
        Customer c = new Customer();
        c.setCustomerId(rs.getString("customer_id"));
        c.setName(rs.getString("name"));
        Date dob = rs.getDate("date_of_birth");
        if (dob != null) {
            c.setDateOfBirth(dob.toLocalDate());
        }
        c.setMobile(rs.getString("mobile"));
        c.setAddress(rs.getString("address"));
        c.setKycDetails(rs.getString("kyc_details"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            c.setCreatedAt(ts.toInstant().atOffset(ZoneOffset.UTC));
        }
        return c;
    }
}
