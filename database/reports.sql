
-- CineReserve Reports
-- CockroachDB

-- 1. Movie show and seat availability
SELECT
    show_id,
    movie_title,
    theatre_name,
    city,
    screen_name,
    show_time,
    total_seats,
    available_seats,
    base_price
FROM vw_show_availability
ORDER BY show_time;

-- 2. Booking details and payment status
SELECT
    b.booking_id,
    u.full_name,
    m.title AS movie_title,
    b.booking_status,
    b.total_amount,
    p.payment_status,
    p.amount AS payment_amount
FROM Bookings b
JOIN Users u ON u.user_id = b.user_id
JOIN Movie_Shows ms ON ms.show_id = b.show_id
JOIN Movies m ON m.movie_id = ms.movie_id
LEFT JOIN Payments p ON p.booking_id = b.booking_id
ORDER BY b.booking_id;

-- 3. Movie-wise successful revenue
SELECT
    m.title AS movie_title,
    COUNT(DISTINCT b.booking_id) AS total_bookings,
    COALESCE(
        SUM(
            CASE
                WHEN p.payment_status = 'SUCCESS'
                THEN p.amount
                ELSE 0
            END
        ), 0
    ) AS total_revenue
FROM Movies m
LEFT JOIN Movie_Shows ms ON ms.movie_id = m.movie_id
LEFT JOIN Bookings b ON b.show_id = ms.show_id
LEFT JOIN Payments p ON p.booking_id = b.booking_id
GROUP BY m.movie_id, m.title
ORDER BY m.title;

-- 4. Total successful payments
SELECT
    COUNT(*) AS successful_payment_count,
    COALESCE(SUM(amount), 0) AS successful_payment_total
FROM Payments
WHERE payment_status = 'SUCCESS';
