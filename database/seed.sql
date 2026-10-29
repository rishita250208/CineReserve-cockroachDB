-- CineReserve demo data for CockroachDB.
-- Safe to rerun: inserts match natural keys and never delete existing rows.
-- The only row update keeps the sample booking's seat marked as BOOKED.

-- Resynchronize generated IDs after legacy seeds that supplied explicit IDs.
-- setval changes only sequence counters; it does not modify table rows.
SELECT setval('users_user_id_seq', COALESCE((SELECT MAX(user_id) + 1 FROM users), 1), false);
SELECT setval('movies_movie_id_seq', COALESCE((SELECT MAX(movie_id) + 1 FROM movies), 1), false);
SELECT setval('theatres_theatre_id_seq', COALESCE((SELECT MAX(theatre_id) + 1 FROM theatres), 1), false);
SELECT setval('screens_screen_id_seq', COALESCE((SELECT MAX(screen_id) + 1 FROM screens), 1), false);
SELECT setval('seats_seat_id_seq', COALESCE((SELECT MAX(seat_id) + 1 FROM seats), 1), false);
SELECT setval('movie_shows_show_id_seq', COALESCE((SELECT MAX(show_id) + 1 FROM movie_shows), 1), false);
SELECT setval('show_seats_show_seat_id_seq', COALESCE((SELECT MAX(show_seat_id) + 1 FROM show_seats), 1), false);
SELECT setval('bookings_booking_id_seq', COALESCE((SELECT MAX(booking_id) + 1 FROM bookings), 1), false);
SELECT setval('tickets_ticket_id_seq', COALESCE((SELECT MAX(ticket_id) + 1 FROM tickets), 1), false);
SELECT setval('payments_payment_id_seq', COALESCE((SELECT MAX(payment_id) + 1 FROM payments), 1), false);


INSERT INTO movies (title, genre, language, duration_minutes, certificate, release_date, description)
SELECT 'Kalki 2898 AD', 'Sci-Fi', 'Hindi', 181, 'UA', DATE '2024-06-27', 'Science fiction adventure'
WHERE NOT EXISTS (SELECT 1 FROM movies WHERE title='Kalki 2898 AD' AND language='Hindi');
INSERT INTO movies (title, genre, language, duration_minutes, certificate, release_date, description)
SELECT 'Stree 2', 'Horror Comedy', 'Hindi', 149, 'UA', DATE '2024-08-15', 'Horror comedy'
WHERE NOT EXISTS (SELECT 1 FROM movies WHERE title='Stree 2' AND language='Hindi');
INSERT INTO movies (title, genre, language, duration_minutes, certificate, release_date, description)
SELECT 'Avengers Endgame', 'Action', 'English', 181, 'UA', DATE '2019-04-26', 'Superhero action adventure'
WHERE NOT EXISTS (SELECT 1 FROM movies WHERE title='Avengers Endgame' AND language='English');

INSERT INTO theatres (theatre_name, city, address)
SELECT 'CineReserve Central', 'Indore', 'MG Road, Indore'
WHERE NOT EXISTS (SELECT 1 FROM theatres WHERE theatre_name='CineReserve Central' AND city='Indore');
INSERT INTO theatres (theatre_name, city, address)
SELECT 'CineReserve Galaxy', 'Bhopal', 'MP Nagar, Bhopal'
WHERE NOT EXISTS (SELECT 1 FROM theatres WHERE theatre_name='CineReserve Galaxy' AND city='Bhopal');

INSERT INTO screens (theatre_id, screen_name, screen_type)
SELECT t.theatre_id, 'Screen 1', '2D' FROM theatres t
WHERE t.theatre_name='CineReserve Central' AND t.city='Indore'
AND NOT EXISTS (SELECT 1 FROM screens s WHERE s.theatre_id=t.theatre_id AND s.screen_name='Screen 1');
INSERT INTO screens (theatre_id, screen_name, screen_type)
SELECT t.theatre_id, 'Screen 2', '3D' FROM theatres t
WHERE t.theatre_name='CineReserve Central' AND t.city='Indore'
AND NOT EXISTS (SELECT 1 FROM screens s WHERE s.theatre_id=t.theatre_id AND s.screen_name='Screen 2');
INSERT INTO screens (theatre_id, screen_name, screen_type)
SELECT t.theatre_id, 'Screen 1', '2D' FROM theatres t
WHERE t.theatre_name='CineReserve Galaxy' AND t.city='Bhopal'
AND NOT EXISTS (SELECT 1 FROM screens s WHERE s.theatre_id=t.theatre_id AND s.screen_name='Screen 1');

INSERT INTO seats (screen_id, seat_row, seat_number, seat_type, seat_price)
SELECT s.screen_id, 'A', 1, 'REGULAR', 150 FROM screens s JOIN theatres t USING (theatre_id)
WHERE t.theatre_name='CineReserve Central' AND t.city='Indore' AND s.screen_name='Screen 1'
AND NOT EXISTS (SELECT 1 FROM seats x WHERE x.screen_id=s.screen_id AND x.seat_row='A' AND x.seat_number=1);
INSERT INTO seats (screen_id, seat_row, seat_number, seat_type, seat_price)
SELECT s.screen_id, 'A', 2, 'REGULAR', 150 FROM screens s JOIN theatres t USING (theatre_id)
WHERE t.theatre_name='CineReserve Central' AND t.city='Indore' AND s.screen_name='Screen 1'
AND NOT EXISTS (SELECT 1 FROM seats x WHERE x.screen_id=s.screen_id AND x.seat_row='A' AND x.seat_number=2);
INSERT INTO seats (screen_id, seat_row, seat_number, seat_type, seat_price)
SELECT s.screen_id, 'A', 1, 'PREMIUM', 220 FROM screens s JOIN theatres t USING (theatre_id)
WHERE t.theatre_name='CineReserve Central' AND t.city='Indore' AND s.screen_name='Screen 2'
AND NOT EXISTS (SELECT 1 FROM seats x WHERE x.screen_id=s.screen_id AND x.seat_row='A' AND x.seat_number=1);
INSERT INTO seats (screen_id, seat_row, seat_number, seat_type, seat_price)
SELECT s.screen_id, 'A', 2, 'PREMIUM', 220 FROM screens s JOIN theatres t USING (theatre_id)
WHERE t.theatre_name='CineReserve Central' AND t.city='Indore' AND s.screen_name='Screen 2'
AND NOT EXISTS (SELECT 1 FROM seats x WHERE x.screen_id=s.screen_id AND x.seat_row='A' AND x.seat_number=2);
INSERT INTO seats (screen_id, seat_row, seat_number, seat_type, seat_price)
SELECT s.screen_id, 'A', 1, 'REGULAR', 160 FROM screens s JOIN theatres t USING (theatre_id)
WHERE t.theatre_name='CineReserve Galaxy' AND t.city='Bhopal' AND s.screen_name='Screen 1'
AND NOT EXISTS (SELECT 1 FROM seats x WHERE x.screen_id=s.screen_id AND x.seat_row='A' AND x.seat_number=1);
INSERT INTO seats (screen_id, seat_row, seat_number, seat_type, seat_price)
SELECT s.screen_id, 'A', 2, 'REGULAR', 160 FROM screens s JOIN theatres t USING (theatre_id)
WHERE t.theatre_name='CineReserve Galaxy' AND t.city='Bhopal' AND s.screen_name='Screen 1'
AND NOT EXISTS (SELECT 1 FROM seats x WHERE x.screen_id=s.screen_id AND x.seat_row='A' AND x.seat_number=2);

INSERT INTO movie_shows (movie_id, screen_id, show_time, base_price)
SELECT m.movie_id, s.screen_id, TIMESTAMP '2026-11-01 18:30:00', 180 FROM movies m CROSS JOIN screens s JOIN theatres t USING (theatre_id)
WHERE m.title='Kalki 2898 AD' AND s.screen_name='Screen 1' AND t.theatre_name='CineReserve Central'
AND NOT EXISTS (SELECT 1 FROM movie_shows x WHERE x.movie_id=m.movie_id AND x.screen_id=s.screen_id AND x.show_time=TIMESTAMP '2026-11-01 18:30:00');
INSERT INTO movie_shows (movie_id, screen_id, show_time, base_price)
SELECT m.movie_id, s.screen_id, TIMESTAMP '2026-11-01 19:00:00', 220 FROM movies m CROSS JOIN screens s JOIN theatres t USING (theatre_id)
WHERE m.title='Stree 2' AND s.screen_name='Screen 2' AND t.theatre_name='CineReserve Central'
AND NOT EXISTS (SELECT 1 FROM movie_shows x WHERE x.movie_id=m.movie_id AND x.screen_id=s.screen_id AND x.show_time=TIMESTAMP '2026-11-01 19:00:00');
INSERT INTO movie_shows (movie_id, screen_id, show_time, base_price)
SELECT m.movie_id, s.screen_id, TIMESTAMP '2026-11-01 17:30:00', 160 FROM movies m CROSS JOIN screens s JOIN theatres t USING (theatre_id)
WHERE m.title='Avengers Endgame' AND s.screen_name='Screen 1' AND t.theatre_name='CineReserve Galaxy'
AND NOT EXISTS (SELECT 1 FROM movie_shows x WHERE x.movie_id=m.movie_id AND x.screen_id=s.screen_id AND x.show_time=TIMESTAMP '2026-11-01 17:30:00');

INSERT INTO show_seats (show_id, seat_id, seat_status)
SELECT ms.show_id, s.seat_id, 'AVAILABLE' FROM movie_shows ms JOIN seats s ON s.screen_id=ms.screen_id
WHERE NOT EXISTS (SELECT 1 FROM show_seats x WHERE x.show_id=ms.show_id AND x.seat_id=s.seat_id);

INSERT INTO users (full_name, email, phone, password_hash)
SELECT 'Demo Customer', 'demo@cinereserve.test', '9000000001', 'DEMO_HASH_NOT_FOR_LOGIN'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email='demo@cinereserve.test');

-- Seed only one coherent example booking and related records when absent.
INSERT INTO bookings (user_id, show_id, booking_status, total_amount)
SELECT u.user_id, ms.show_id, 'CONFIRMED', ms.base_price
FROM users u JOIN movie_shows ms ON ms.show_time=TIMESTAMP '2026-11-01 18:30:00'
JOIN movies m ON m.movie_id=ms.movie_id
WHERE u.email='demo@cinereserve.test' AND m.title='Kalki 2898 AD'
AND NOT EXISTS (SELECT 1 FROM bookings b WHERE b.user_id=u.user_id AND b.show_id=ms.show_id);
UPDATE show_seats SET seat_status='BOOKED'
WHERE show_id IN (SELECT ms.show_id FROM movie_shows ms JOIN movies m ON m.movie_id=ms.movie_id WHERE m.title='Kalki 2898 AD' AND ms.show_time=TIMESTAMP '2026-11-01 18:30:00')
AND seat_id IN (SELECT s.seat_id FROM seats s JOIN screens sc ON sc.screen_id=s.screen_id WHERE sc.screen_name='Screen 1' AND s.seat_row='A' AND s.seat_number=1)
AND EXISTS (SELECT 1 FROM bookings b JOIN users u ON u.user_id=b.user_id WHERE u.email='demo@cinereserve.test' AND b.booking_status='CONFIRMED');
INSERT INTO tickets (booking_id, show_seat_id, ticket_price, ticket_status)
SELECT b.booking_id, ss.show_seat_id, ms.base_price, 'VALID'
FROM bookings b JOIN users u ON u.user_id=b.user_id JOIN movie_shows ms ON ms.show_id=b.show_id
JOIN movies m ON m.movie_id=ms.movie_id JOIN show_seats ss ON ss.show_id=ms.show_id
JOIN seats s ON s.seat_id=ss.seat_id
WHERE u.email='demo@cinereserve.test' AND m.title='Kalki 2898 AD' AND s.seat_row='A' AND s.seat_number=1
AND NOT EXISTS (SELECT 1 FROM tickets t WHERE t.show_seat_id=ss.show_seat_id);
INSERT INTO payments (booking_id, payment_method, payment_status, amount, transaction_ref)
SELECT b.booking_id, 'UPI', 'SUCCESS', b.total_amount, CONCAT('DEMO-SEED-', b.booking_id)
FROM bookings b JOIN users u ON u.user_id=b.user_id JOIN movie_shows ms ON ms.show_id=b.show_id
JOIN movies m ON m.movie_id=ms.movie_id
WHERE u.email='demo@cinereserve.test' AND m.title='Kalki 2898 AD'
AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.booking_id=b.booking_id);
