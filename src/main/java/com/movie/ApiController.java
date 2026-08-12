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
        db.post("/movies", movieJson); 
        return ResponseEntity.ok("Added");
    }

    @GetMapping("/bookings")
    public ResponseEntity<?> getBookings(@RequestParam String email) throws Exception {
        String res = db.get("/bookings/" + encodeEmail(email));
        return ResponseEntity.ok(res != null && !res.equals("null") ? res : "{}");
    }

    @PostMapping("/bookings")
    public ResponseEntity<?> addBooking(@RequestParam String email, @RequestBody String bookingJson) throws Exception {
        JSONObject b = new JSONObject(bookingJson);
        
        // --- SEAT DEDUCTION LOGIC ---
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
            // --- RETURN SEATS TO MOVIE HALL ---
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
            
            // Delete the booking entirely
            bookingsObj.remove(bookingToDeleteId);
            db.put("/bookings/" + encodedEmail, bookingsObj.isEmpty() ? "{}" : bookingsObj.toString());
            return ResponseEntity.ok("Ticket permanently erased.");
        }
        return ResponseEntity.badRequest().body("Ticket not found.");
    }

    @PostMapping("/validate-ticket")
    public ResponseEntity<?> validateTicket(@RequestBody Map<String, String> payload) throws Exception {
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
}
