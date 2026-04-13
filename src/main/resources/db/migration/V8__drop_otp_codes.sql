-- OTP désormais stockés dans Redis (cf. RedisOtpStore).
-- La table Postgres n'est plus lue ni écrite par le code applicatif.
DROP TABLE IF EXISTS otp_codes;
