-- Demo data for local development only.
-- All demo accounts use the password: Password123!

INSERT INTO departments (name) VALUES
    ('Information Technology'),
    ('Facilities'),
    ('Operations');

INSERT INTO users (email, password_hash, full_name, phone, role_id, department_id) VALUES
    ('admin@servicehub.local',
     '$2y$10$dIq9X/VdQ/P72NgrAuMwHOXE4/apDk5EkG96b7f7MqRDEVQVfHdiy',
     'System Administrator', '+10000000001', 1,
     (SELECT id FROM departments WHERE name = 'Information Technology')),

    ('manager@servicehub.local',
     '$2y$10$dIq9X/VdQ/P72NgrAuMwHOXE4/apDk5EkG96b7f7MqRDEVQVfHdiy',
     'Mia Manager', '+10000000002', 4,
     (SELECT id FROM departments WHERE name = 'Information Technology')),

    ('tech@servicehub.local',
     '$2y$10$dIq9X/VdQ/P72NgrAuMwHOXE4/apDk5EkG96b7f7MqRDEVQVfHdiy',
     'Tom Technician', '+10000000003', 3,
     (SELECT id FROM departments WHERE name = 'Information Technology')),

    ('employee@servicehub.local',
     '$2y$10$dIq9X/VdQ/P72NgrAuMwHOXE4/apDk5EkG96b7f7MqRDEVQVfHdiy',
     'Eve Employee', '+10000000004', 2,
     (SELECT id FROM departments WHERE name = 'Information Technology'));
