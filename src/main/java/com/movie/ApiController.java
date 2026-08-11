package com.movie;

import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ApiController {
    private final FirebaseService db;

    public ApiController(FirebaseService db) { this.db = db; }

    private String encodeEmail(String email) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(email.getBytes());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> creds) {
        String email = creds.get("email");
        String pass = creds.get("password");

        if (email.equals("sanchar@admin.com") && pass.equals("T!ger5243")) {
            return ResponseEntity.ok(Map.of("status", "success", "role", "admin", "email", email));
        }
        try {
            String result = db.get("/users/" + encodeEmail(email));
            if (result == null || result.equals("null")) return ResponseEntity.badRequest().body("User not found.");
            JSONObject user = new JSONObject(result);
            if (user.getString("password").equals(pass)) {
                return ResponseEntity.ok(Map.of("status", "success", "role", "user", "email", email));
            } else {
                return ResponseEntity.badRequest().body("Incorrect password.");
            }
        } catch (Exception e) { return ResponseEntity.internalServerError().body("Database error."); }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> data) {
        String email = data.get("email");
        String safeKey = encodeEmail(email);
        try {
            if (!"null".equals(db.get("/users/" + safeKey))) return ResponseEntity.badRequest().body("Email exists.");
            JSONObject user = new JSONObject();
            user.put("email", email); user.put("password", data.get("password"));
            db.put("/users/" + safeKey, user.toString());
            return ResponseEntity.ok("Success");
        } catch (Exception e) { return ResponseEntity.internalServerError().body("Error."); }
    }

    @GetMapping("/movies")
    public ResponseEntity<?> getMovies() throws Exception {
        String res = db.get("/movies");
        return ResponseEntity.ok(res != null && !res.equals("null") ? res : "{}");
    }

    @PostMapping("/movies")
    public ResponseEntity<?> addMovie(@RequestBody String movieJson) throws Exception {
        db.post("/movies", movieJson); return ResponseEntity.ok("Added");
    }

    @GetMapping("/bookings")
    public ResponseEntity<?> getBookings(@RequestParam String email) throws Exception {
        String res = db.get("/bookings/" + encodeEmail(email));
        return ResponseEntity.ok(res != null && !res.equals("null") ? res : "{}");
    }

    @PostMapping("/bookings")
    public ResponseEntity<?> addBooking(@RequestParam String email, @RequestBody String bookingJson) throws Exception {
        JSONObject b = new JSONObject(bookingJson);

        // Generate a random 6-character code
        String ticketCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        b.put("ticketCode", ticketCode);
        b.put("status", "Valid");

        db.post("/bookings/" + encodeEmail(email), b.toString());
        return ResponseEntity.ok(Map.of("message", "Booked", "ticketCode", ticketCode));
    }

    @PostMapping("/validate-ticket")
    public ResponseEntity<?> validateTicket(@RequestBody Map<String, String> payload) throws Exception {
        String code = payload.get("code");
        String allBookings = db.get("/bookings");

        if (allBookings == null || allBookings.equals("null")) {
            return ResponseEntity.badRequest().body("Database is empty.");
        }

        JSONObject bookingsObj = new JSONObject(allBookings);

        // Iterates through every user's bookings to find the matching 6-character code
        for (String emailKey : bookingsObj.keySet()) {
            JSONObject userBookings = bookingsObj.getJSONObject(emailKey);
            for (String pushId : userBookings.keySet()) {
                JSONObject b = userBookings.getJSONObject(pushId);

                if (b.has("ticketCode") && b.getString("ticketCode").equals(code)) {
                    if (b.optString("status", "Valid").equals("Used")) {
                        return ResponseEntity.badRequest().body("❌ TICKET ALREADY USED!");
                    }

                    // Mark as Used and save back to Realtime Database
                    b.put("status", "Used");
                    db.put("/bookings/" + emailKey + "/" + pushId, b.toString());

                    return ResponseEntity.ok(Map.of(
                            "message", "✅ Ticket Validated & Executed!",
                            "movie", b.getString("movie"),
                            "tickets", String.valueOf(b.getInt("tickets"))
                    ));
                }
            }
        }
        return ResponseEntity.badRequest().body("❌ Invalid Ticket Code.");
    }
}