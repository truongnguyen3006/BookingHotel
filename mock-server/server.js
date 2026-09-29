const http = require("http");

const HOST = "127.0.0.1";
const PORT = Number(process.env.PORT || 8080);

let nextBookingId = 1;
const bookings = new Map();

let rooms = [
  {
    id: 1,
    imageKey: "standard_room",
    typeKey: "standard",
    pricePerNight: 50.0,
    amenities: ["Wi-Fi", "TV"],
    availableRooms: 10,
  },
  {
    id: 2,
    imageKey: "deluxe_room",
    typeKey: "deluxe",
    pricePerNight: 80.0,
    amenities: ["Wi-Fi", "TV", "Mini Bar"],
    availableRooms: 10,
  },
  {
    id: 3,
    imageKey: "suite_room",
    typeKey: "suite",
    pricePerNight: 120.0,
    amenities: ["Wi-Fi", "TV", "Mini Bar", "Jacuzzi"],
    availableRooms: 10,
  },
  {
    id: 4,
    imageKey: "executive_room",
    typeKey: "executive",
    pricePerNight: 150.0,
    amenities: ["Wi-Fi", "TV", "Mini Bar", "Jacuzzi", "Breakfast"],
    availableRooms: 10,
  },
  {
    id: 5,
    imageKey: "family_room",
    typeKey: "family",
    pricePerNight: 100.0,
    amenities: ["Wi-Fi", "TV", "Kitchenette"],
    availableRooms: 10,
  },
];

function sendJson(res, statusCode, body) {
  const json = JSON.stringify(body);
  res.writeHead(statusCode, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(json),
  });
  res.end(json);
}

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    let body = "";
    req.on("data", (chunk) => {
      body += chunk;
      if (body.length > 1_000_000) {
        reject(new Error("Request body too large"));
        req.destroy();
      }
    });
    req.on("end", () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch (error) {
        reject(error);
      }
    });
    req.on("error", reject);
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);

  if (req.method === "GET" && url.pathname === "/health") {
    return sendJson(res, 200, { status: "UP" });
  }

  if (req.method === "GET" && url.pathname === "/api/rooms") {
    return sendJson(res, 200, rooms);
  }

  const roomMatch = url.pathname.match(/^\/api\/rooms\/(\d+)$/);
  if (req.method === "GET" && roomMatch) {
    const roomId = Number(roomMatch[1]);
    const room = rooms.find((item) => item.id === roomId);
    return room
      ? sendJson(res, 200, room)
      : sendJson(res, 404, { message: "Room not found" });
  }

  if (req.method === "POST" && url.pathname === "/api/bookings") {
    try {
      const body = await readJsonBody(req);
      const roomId = Number(body.roomId);
      const quantity = Number(body.quantity);
      const roomIndex = rooms.findIndex((item) => item.id === roomId);

      if (roomIndex === -1) {
        return sendJson(res, 404, { message: "Room not found" });
      }

      if (!Number.isInteger(quantity) || quantity <= 0) {
        return sendJson(res, 400, { message: "Quantity must be greater than zero" });
      }

      const room = rooms[roomIndex];
      if (quantity > room.availableRooms) {
        return sendJson(res, 409, { message: "Not enough rooms available" });
      }

      const updatedRoom = {
        ...room,
        availableRooms: room.availableRooms - quantity,
      };
      rooms[roomIndex] = updatedRoom;

      const booking = {
        bookingId: nextBookingId++,
        room: updatedRoom,
        quantity,
        totalPrice: updatedRoom.pricePerNight * quantity,
        status: "PENDING_PAYMENT",
      };

      bookings.set(booking.bookingId, booking);
      return sendJson(res, 201, booking);
    } catch (error) {
      return sendJson(res, 400, { message: "Invalid JSON request body" });
    }
  }

  const paymentMatch = url.pathname.match(/^\/api\/bookings\/(\d+)\/payment$/);
  if (req.method === "POST" && paymentMatch) {
    try {
      const bookingId = Number(paymentMatch[1]);
      const booking = bookings.get(bookingId);

      if (!booking) {
        return sendJson(res, 404, { message: "Booking not found" });
      }

      if (booking.status === "SUCCESS") {
        return sendJson(res, 409, { message: "Booking already paid" });
      }

      const body = await readJsonBody(req);
      const method = String(body.method || "").toUpperCase();
      const simulateFailure = body.simulateFailure === true;

      if (!['CARD', 'QR'].includes(method)) {
        return sendJson(res, 400, { message: "Unsupported payment method" });
      }

      if (simulateFailure) {
        const failedBooking = {
          ...booking,
          status: "FAILED",
        };
        bookings.set(bookingId, failedBooking);

        return sendJson(res, 200, {
          bookingId,
          status: "FAILED",
          method,
          transactionId: null,
          message: "Payment was declined (simulated)",
        });
      }

      const transactionId = `TXN-${bookingId}-${Date.now()}`;
      const paidBooking = {
        ...booking,
        status: "SUCCESS",
      };
      bookings.set(bookingId, paidBooking);

      return sendJson(res, 200, {
        bookingId,
        status: "SUCCESS",
        method,
        transactionId,
        message: "Payment completed successfully",
      });
    } catch (error) {
      return sendJson(res, 400, { message: "Invalid payment request" });
    }
  }

  const bookingMatch = url.pathname.match(/^\/api\/bookings\/(\d+)$/);
  if (req.method === "GET" && bookingMatch) {
    const bookingId = Number(bookingMatch[1]);
    const booking = bookings.get(bookingId);
    return booking
      ? sendJson(res, 200, booking)
      : sendJson(res, 404, { message: "Booking not found" });
  }

  return sendJson(res, 404, { message: "Endpoint not found" });
});

server.listen(PORT, HOST, () => {
  console.log(`Mock Hotel API is running at http://${HOST}:${PORT}`);
  console.log(`Android Emulator base URL: http://10.0.2.2:${PORT}/`);
  console.log(`Health check: http://${HOST}:${PORT}/health`);
});
