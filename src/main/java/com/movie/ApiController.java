package com.movie;

import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ApiController {
    private final FirebaseService db;
    
    // Secret Server Token (Only given to the true Admin)
    private static final String ADMIN_SECRET = "SK_SECURE_ADMIN_TOKEN_908234";

    public ApiController(FirebaseService db) { this.db = db; }

    private String encodeEmail(String email) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(email.getBytes());
    }

    // SHA-256 Hashing Algorithm
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) { throw new RuntimeException("Encryption Error"); }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> creds) {
        String email = creds.get("email");
        String pass = creds.get("password");

        // Hardcoded Admin Auth -> Grants Secret Token
        if (email.equals("sanchar@admin.com") && pass.equals("T!ger5243")) {
            return ResponseEntity.ok(Map.of("status", "success", "role", "admin", "email", email, "token", ADMIN_SECRET));
        }
        
        // Standard User Auth -> Verifies Hashed Password
        try {
            String result = db.get("/users/" + encodeEmail(email));
            if (result == null || result.equals("null")) return ResponseEntity.badRequest().body("User not found.");
            
            JSONObject user = new JSONObject(result);
            if (user.getString("password").equals(hashPassword(pass))) {
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
            user.put("email", email); 
            user.put("password", hashPassword(data.get("password"))); // Hashes password before saving
            
            db.put("/users/" + safeKey, user.toString());
            return ResponseEntity.ok("Success");
        } catch (Exception e) { return ResponseEntity.internalServerError().body("Error."); }
    }

    @GetMapping("/movies")
    public ResponseEntity<?> getMovies() throws Exception {
        String res = db.get("/movies");
        return ResponseEntity.ok(res != null && !res.equals("null") ? res : "{}");
    }

    // ================= SECURED ADMIN ROUTES =================
    
    @PostMapping("/movies")
    public ResponseEntity<?> addMovie(@RequestHeader(value = "Admin-Token", defaultValue = "") String token, @RequestBody String movieJson) throws Exception {
        if (!ADMIN_SECRET.equals(token)) return ResponseEntity.status(403).body("Unauthorized");
        db.post("/movies", movieJson); 
        return ResponseEntity.ok("Added");
    }

    @PutMapping("/movies")
    public ResponseEntity<?> updateMovie(@RequestHeader(value = "Admin-Token", defaultValue = "") String token, @RequestParam String id, @RequestBody String movieJson) throws Exception {
        if (!ADMIN_SECRET.equals(token)) return ResponseEntity.status(403).body("Unauthorized");
        db.put("/movies/" + id, movieJson);
        return ResponseEntity.ok("Updated");
    }

    @DeleteMapping("/movies")
    public ResponseEntity<?> deleteMovie(@RequestHeader(value = "Admin-Token", defaultValue = "") String token, @RequestParam String id) throws Exception {
        if (!ADMIN_SECRET.equals(token)) return ResponseEntity.status(403).body("Unauthorized");
        String res = db.get("/movies");
        if (res != null && !res.equals("null")) {
            JSONObject movies = new JSONObject(res);
            if (movies.has(id)) {
                movies.remove(id);
                db.put("/movies", movies.isEmpty() ? "{}" : movies.toString());
                return ResponseEntity.ok("Deleted");
            }
        }
        return ResponseEntity.badRequest().body("Not found");
    }

    @PostMapping("/validate-ticket")
    public ResponseEntity<?> validateTicket(@RequestHeader(value = "Admin-Token", defaultValue = "") String token, @RequestBody Map<String, String> payload) throws Exception {
        if (!ADMIN_SECRET.equals(token)) return ResponseEntity.status(403).body("Unauthorized");
        
        String code = payload.get("code");
        String allBookings = db.get("/bookings");
        if (allBookings == null || allBookings.equals("null")) return ResponseEntity.badRequest().body("Database is empty.");
        
        JSONObject bookingsObj = new JSONObject(allBookings);
        for (String emailKey : bookingsObj.keySet()) {
            JSONObject userBookings = bookingsObj.getJSONObject(emailKey);
            for (String pushId : userBookings.keySet()) {
                JSONObject b = userBookings.getJSONObject(pushId);
                if (b.has("ticketCode") && b.getString("ticketCode").equals(code)) {
                    if (b.optString("status", "Valid").equals("Used")) return ResponseEntity.badRequest().body("❌ TICKET ALREADY USED!");
                    b.put("status", "Used");
                    db.put("/bookings/" + emailKey + "/" + pushId, b.toString());
                    return ResponseEntity.ok(Map.of("message", "✅ Ticket Validated!", "movie", b.getString("movie"), "tickets", String.valueOf(b.getInt("tickets"))));
                }
            }
        }
        return ResponseEntity.badRequest().body("❌ Invalid Ticket Code.");
    }
    
    // ================= STANDARD USER ROUTES =================

    @GetMapping("/bookings")
    public ResponseEntity<?> getBookings(@RequestParam String email) throws Exception {
        String res = db.get("/bookings/" + encodeEmail(email));
        return ResponseEntity.ok(res != null && !res.equals("null") ? res : "{}");
    }

    @PostMapping("/bookings")
    public ResponseEntity<?> addBooking(@RequestParam String email, @RequestBody String bookingJson) throws Exception {
        JSONObject b = new JSONObject(bookingJson);
        String moviesRes = db.get("/movies");
        if (moviesRes != null && !moviesRes.equals("null")) {
            JSONObject moviesObj = new JSONObject(moviesRes);
            String targetMovieId = null;
            JSONObject targetMovie = null;
            
            for (String key : moviesObj.keySet()) {
                JSONObject m = moviesObj.getJSONObject(key);
                if (m.getString("title").equals(b.getString("movie")) &&
                    (m.getString("hall") + " - " + m.getString("location")).equals(b.getString("location")) &&
                    m.getString("time").equals(b.getString("time"))) {
                    targetMovieId = key;
                    targetMovie = m;
                    break;
                }
            }
            
            if (targetMovie != null) {
                int availableSeats = targetMovie.getInt("seats");
                int requestedTickets = b.getInt("tickets");
                if (availableSeats < requestedTickets) {
                    return ResponseEntity.badRequest().body("Not enough seats available!");
                }
                targetMovie.put("seats", availableSeats - requestedTickets);
                db.put("/movies/" + targetMovieId, targetMovie.toString());
            }
        }

        String ticketCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        b.put("ticketCode", ticketCode);
        b.put("status", "Valid");
        
        db.post("/bookings/" + encodeEmail(email), b.toString()); 
        return ResponseEntity.ok(Map.of("message", "Booked", "ticketCode", ticketCode));
    }

    @DeleteMapping("/bookings")
    public ResponseEntity<?> deleteBooking(@RequestParam String email, @RequestParam String code) throws Exception {
        String encodedEmail = encodeEmail(email);
        String allBookings = db.get("/bookings/" + encodedEmail);
        
        if (allBookings == null || allBookings.equals("null")) {
            return ResponseEntity.badRequest().body("Database is empty.");
        }
        
        JSONObject bookingsObj = new JSONObject(allBookings);
        String bookingToDeleteId = null;
        JSONObject bookingToDeleteObj = null;
        
        for (String key : bookingsObj.keySet()) {
            JSONObject b = bookingsObj.getJSONObject(key);
            if (b.has("ticketCode") && b.getString("ticketCode").equals(code)) {
                bookingToDeleteId = key;
                bookingToDeleteObj = b;
                break;
            }
        }
        
        if (bookingToDeleteId != null) {
            String moviesRes = db.get("/movies");
            if (moviesRes != null && !moviesRes.equals("null") && bookingToDeleteObj != null) {
                JSONObject moviesObj = new JSONObject(moviesRes);
                for (String key : moviesObj.keySet()) {
                    JSONObject m = moviesObj.getJSONObject(key);
                    if (m.getString("title").equals(bookingToDeleteObj.getString("movie")) &&
                        (m.getString("hall") + " - " + m.getString("location")).equals(bookingToDeleteObj.getString("location")) &&
                        m.getString("time").equals(bookingToDeleteObj.getString("time"))) {
                        
                        int currentSeats = m.getInt("seats");
                        m.put("seats", currentSeats + bookingToDeleteObj.getInt("tickets"));
                        db.put("/movies/" + key, m.toString());
                        break;
                    }
                }
            }
            
            bookingsObj.remove(bookingToDeleteId);
            db.put("/bookings/" + encodedEmail, bookingsObj.isEmpty() ? "{}" : bookingsObj.toString());
            return ResponseEntity.ok("Ticket permanently erased.");
        }
        return ResponseEntity.badRequest().body("Ticket not found.");
    }
}
