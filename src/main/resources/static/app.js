let currentUser = null;
let allMoviesData = [];
const API_BASE = "https://your-render-url-here.onrender.com"; // UPDATE THIS ONCE RENDER IS LIVE

// PERSISTENCE CHECK ON BOOT
document.addEventListener("DOMContentLoaded", () => {
    const savedSession = localStorage.getItem('movieAppUser');
    if (savedSession) {
        currentUser = JSON.parse(savedSession);
        routeToDashboard();
    }
});

function showToast(msg, isErr = false) {
    const t = document.getElementById('toast'); t.textContent = msg; t.className = `toast show ${isErr ? 'error' : ''}`;
    setTimeout(() => t.classList.remove('show'), 3000);
}

function switchView(id) { document.querySelectorAll('.view-container').forEach(el => el.classList.remove('active')); document.getElementById(id).classList.add('active'); }
function switchAuthTab(t) {
    document.querySelectorAll('.tab-header button').forEach(b => b.classList.remove('active')); document.getElementById(`tab-${t}`).classList.add('active');
    document.querySelectorAll('.auth-form').forEach(f => f.classList.remove('active')); document.getElementById(`${t}-form`).classList.add('active');
}

function routeToDashboard() {
    if (currentUser.role === 'admin') {
        updateAdminLocs();
        switchView('admin-view');
    } else {
        loadAppMovies();
        loadUserBookings();
        switchView('user-view');
    }
}

// LOGIN & LOGOUT
document.getElementById('login-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const payload = { email: document.getElementById('log-email').value, password: document.getElementById('log-pass').value };
    try {
        const res = await fetch(`${API_BASE}/api/login`, { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(payload) });
        if (!res.ok) throw new Error(await res.text());
        currentUser = await res.json();
        localStorage.setItem('movieAppUser', JSON.stringify(currentUser));
        document.getElementById('log-email').value = ''; document.getElementById('log-pass').value = '';
        showToast(`Welcome, ${currentUser.role}!`);
        routeToDashboard();
    } catch (err) { showToast(err.message, true); }
});

function logout() {
    currentUser = null; localStorage.removeItem('movieAppUser');
    showToast("Logged out successfully."); switchView('auth-view');
}

// REGISTRATION
const regEmail = document.getElementById('reg-email'); const regPass = document.getElementById('reg-pass'); const regBtn = document.getElementById('reg-btn');
const rules = { len: v => v.length > 8, up: v => /[A-Z]/.test(v), low: v => /[a-z]/.test(v), num: v => /\d/.test(v), spec: v => /[@$!%*?&\-#^]/.test(v) };

regEmail.addEventListener('blur', () => {
    document.getElementById('email-error').classList.toggle('hidden', /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(regEmail.value) || regEmail.value.length === 0); checkRegForm();
});

regPass.addEventListener('input', () => {
    const val = regPass.value;
    for (let k in rules) {
        const el = document.getElementById(`rule-${k}`);
        if (rules[k](val)) { el.textContent = "✅" + el.textContent.substring(1); el.classList.add('valid'); }
        else { el.textContent = "❌" + el.textContent.substring(1); el.classList.remove('valid'); }
    }
    checkRegForm();
});

function checkRegForm() { regBtn.disabled = !(Object.values(rules).every(r => r(regPass.value)) && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(regEmail.value)); }

document.getElementById('register-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
        const res = await fetch(`${API_BASE}/api/register`, { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ email: regEmail.value, password: regPass.value }) });
        if (!res.ok) throw new Error(await res.text());
        showToast("Account Created! Please Sign in."); regEmail.value = ''; regPass.value = ''; switchAuthTab('login');
    } catch (err) { showToast(err.message, true); }
});

// ADMIN MOVIE ADDER & TICKET VALIDATOR
const qLocs = ["Labim Mall", "Bhatbhateni, Radhe Radhe", "Civil Mall", "City Center"]; const oLocs = ["Eyeplex Mall, Baneshwor", "Kalimati Trade Center, Kalimati"];
function updateAdminLocs() { document.getElementById('admin-loc').innerHTML = (document.getElementById('admin-hall').value === 'QFX' ? qLocs : oLocs).map(l => `<option>${l}</option>`).join(''); }

document.getElementById('admin-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const m = { title: document.getElementById('admin-title').value, time: document.getElementById('admin-time').value, hall: document.getElementById('admin-hall').value, location: document.getElementById('admin-loc').value, seats: parseInt(document.getElementById('admin-seats').value), cost: parseInt(document.getElementById('admin-cost').value) };
    await fetch(`${API_BASE}/api/movies`, { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(m) });
    showToast("Movie Published!"); document.getElementById('admin-title').value = '';
});

document.getElementById('admin-validate-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const code = document.getElementById('admin-ticket-code').value.toUpperCase();
    try {
        const res = await fetch(`${API_BASE}/api/validate-ticket`, { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ code }) });
        if (!res.ok) throw new Error(await res.text());
        const data = await res.json();
        showToast(`${data.message} - Tickets: ${data.tickets}`);
        document.getElementById('admin-ticket-code').value = '';
    } catch (err) { showToast(err.message, true); }
});

// USER LOGIC
async function loadAppMovies() {
    const res = await fetch(`${API_BASE}/api/movies`); const data = await res.json(); allMoviesData = Object.values(data || {});
    const uniqueTitles = [...new Set(allMoviesData.map(m => m.title))];
    document.getElementById('user-movie').innerHTML = uniqueTitles.length ? uniqueTitles.map(t => `<option>${t}</option>`).join('') : '<option disabled>No movies</option>';
    updateUserLocs();
}

function updateUserLocs() {
    const title = document.getElementById('user-movie').value;
    const uniqueLocs = [...new Set(allMoviesData.filter(m => m.title === title).map(m => `${m.hall} - ${m.location}`))];
    document.getElementById('user-loc').innerHTML = uniqueLocs.map(l => `<option>${l}</option>`).join(''); updateUserTimes();
}

function updateUserTimes() {
    const title = document.getElementById('user-movie').value; const loc = document.getElementById('user-loc').value;
    document.getElementById('user-time').innerHTML = allMoviesData.filter(m => m.title === title && `${m.hall} - ${m.location}` === loc).map(m => `<option>${m.time}</option>`).join('');
    updatePriceDisplay();
}

function getSelectedMovie() { return allMoviesData.find(m => m.title === document.getElementById('user-movie').value && `${m.hall} - ${m.location}` === document.getElementById('user-loc').value && m.time === document.getElementById('user-time').value); }

function updatePriceDisplay() {
    const m = getSelectedMovie(); if (!m) return;
    document.getElementById('display-price').textContent = `Price: Rs. ${m.cost}`; document.getElementById('display-seats').textContent = `Seats: ${m.seats}`;
    document.getElementById('display-total').textContent = `Total to Pay: Rs. ${m.cost * (parseInt(document.getElementById('user-tickets').value) || 1)}`;
}

document.getElementById('user-book-form').addEventListener('submit', async (e) => {
    e.preventDefault(); const m = getSelectedMovie(); const tix = parseInt(document.getElementById('user-tickets').value);
    const booking = { movie: m.title, location: `${m.hall} - ${m.location}`, time: m.time, tickets: tix, total_cost: m.cost * tix, payment: "Counter" };

    try {
        const res = await fetch(`${API_BASE}/api/bookings?email=${currentUser.email}`, { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(booking) });
        const data = await res.json();

        showToast("Ticket Booked!");
        loadUserBookings();

        // Trigger QR Code Generation for the new ticket
        generateAndShowQR(data.ticketCode);
    } catch(err) { showToast("Booking failed.", true); }
});

async function loadUserBookings() {
    const res = await fetch(`${API_BASE}/api/bookings?email=${currentUser.email}`); const data = await res.json();
    document.getElementById('bookings-list').innerHTML = data ? Object.values(data).reverse().map(b => `
        <div class="receipt-item">
            <div class="receipt-details">
                <strong>🎬 ${b.movie}</strong> | 🕒 ${b.time} 
                <span class="status-badge ${b.status === 'Used' ? 'status-used' : 'status-valid'}">${b.status || 'Valid'}</span><br>
                📍 ${b.location}<br>
                🎫 Tickets: ${b.tickets} | <span style="color:#00e676;">Rs. ${b.total_cost}</span>
            </div>
            ${b.status !== 'Used' ? `<button class="btn-qr" onclick="generateAndShowQR('${b.ticketCode}')">View QR</button>` : ''}
        </div>`).join('') : "<p>No bookings.</p>";
}

// ================= QR CODE LOGIC =================
function generateAndShowQR(code) {
    const modal = document.getElementById('qr-modal');
    const qrLoader = document.getElementById('qr-loader');
    const qrImage = document.getElementById('qr-image');

    document.getElementById('qr-code-text').innerText = code;
    modal.classList.remove('hidden');
    qrLoader.classList.remove('hidden');
    qrImage.classList.add('hidden');

    const apiQrUrl = `https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=${encodeURIComponent(code)}&color=00e676&bgcolor=151229`;

    const imgLoader = new Image();
    imgLoader.src = apiQrUrl;

    imgLoader.onload = () => {
        qrImage.src = apiQrUrl;
        qrLoader.classList.add('hidden');
        qrImage.classList.remove('hidden');
    };
    imgLoader.onerror = () => {
        qrLoader.innerHTML = "Failed to load QR.";
    };
}

function closeQRModal() {
    document.getElementById('qr-modal').classList.add('hidden');
}

async function downloadQR() {
    const qrImage = document.getElementById('qr-image');
    if (!qrImage.src) return;
    showToast("Preparing download...");

    try {
        const response = await fetch(qrImage.src);
        const blob = await response.blob();
        const blobUrl = URL.createObjectURL(blob);

        const a = document.createElement('a');
        a.href = blobUrl;
        a.download = `MovieTicket_QR_${Date.now()}.png`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(blobUrl);

        showToast("Downloaded QR Code!");
    } catch (err) {
        window.open(qrImage.src, '_blank');
        showToast("Opened image to save manually.");
    }
}