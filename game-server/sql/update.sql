/*
* DB changes since a152e86 (09.06.2024)
 */

DROP TABLE `player_vars`;

-- remove old event items
DELETE FROM inventory WHERE item_id IN (186000111, 188051090, 188051091,188051092, 188051093, 188052625, 188052626, 188052627, 188100124, 188100125);