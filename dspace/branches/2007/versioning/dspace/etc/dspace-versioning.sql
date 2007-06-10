--
-- dspace-versioning.sql
--
-- Version: $Revision: $
--
-- Date:    $Date: $
--
--
-- THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
-- ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
-- LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
-- A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
-- HOLDERS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
-- INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
-- BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
-- OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
-- ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
-- TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
-- USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
-- DAMAGE.
--
--   DSpace Versioning SQL Changes
--
--   Authors:   Robert Graham
--
--   This file is used as-is to modify a database. Therefore,
--   table and view definitions must be ordered correctly.
--
--   Caution: THIS IS POSTGRESQL-SPECIFIC:
--
-- DUMP YOUR DATABASE DUMP YOUR DATABASE DUMP YOUR DATABASE DUMP YOUR DATABASE
ALTER TABLE Item ADD revision integer;
ALTER TABLE Item ADD previous_revision integer; -- An item_id

CREATE OR REPLACE FUNCTION get_next_revision(id integer) RETURNS integer AS $$
DECLARE
	rev integer;
BEGIN
	SELECT INTO rev revision 
	FROM Item 
	WHERE item_id = id;

	RETURN 1 + rev;
END;
$$ LANGUAGE plpgsql;
