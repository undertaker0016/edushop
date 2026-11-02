-- The application has ADMIN and INSTRUCTOR accounts only.
-- If STUDENT is still assigned to a user, the existing FK deliberately makes
-- this migration fail rather than deleting a user-role assignment.
DELETE FROM roles
WHERE name = 'STUDENT';
