package repository

import (
	"database/sql"
	"fmt"
	"math/rand"
	"time"

	"family-guard-backend/internal/models"

	"github.com/lib/pq"
)

type Repository struct {
	db *sql.DB
}

func New(db *sql.DB) *Repository {
	return &Repository{db: db}
}

// ===== User =====

func (r *Repository) CreateUser(u *models.User) error {
	return r.db.QueryRow(
		`INSERT INTO users (email, password_hash, nickname) VALUES ($1,$2,$3) RETURNING id, created_at`,
		u.Email, u.PasswordHash, u.Nickname,
	).Scan(&u.ID, &u.CreatedAt)
}

func (r *Repository) GetUserByEmail(email string) (*models.User, error) {
	u := &models.User{}
	err := r.db.QueryRow(
		`SELECT id,email,password_hash,nickname,created_at FROM users WHERE email=$1`, email,
	).Scan(&u.ID, &u.Email, &u.PasswordHash, &u.Nickname, &u.CreatedAt)
	return u, err
}

func (r *Repository) GetUserByID(id int) (*models.User, error) {
	u := &models.User{}
	err := r.db.QueryRow(
		`SELECT id,email,password_hash,nickname,created_at FROM users WHERE id=$1`, id,
	).Scan(&u.ID, &u.Email, &u.PasswordHash, &u.Nickname, &u.CreatedAt)
	return u, err
}

// ===== Device =====

func (r *Repository) CreateDevice(d *models.Device) error {
	if d.PairingCode == "" {
		d.PairingCode = fmt.Sprintf("%06d", rand.Intn(1000000))
	}
	return r.db.QueryRow(
		`INSERT INTO devices (device_id,device_name,model,owner_id,pairing_code) VALUES ($1,$2,$3,$4,$5) RETURNING id,created_at`,
		d.DeviceID, d.DeviceName, d.Model, d.OwnerID, d.PairingCode,
	).Scan(&d.ID, &d.CreatedAt)
}

func (r *Repository) GetDeviceByDeviceID(deviceID string) (*models.Device, error) {
	d := &models.Device{}
	var ownerID sql.NullInt64
	var lastSeen sql.NullTime
	err := r.db.QueryRow(
		`SELECT id,device_id,device_name,model,owner_id,pairing_code,is_online,last_seen_at,created_at FROM devices WHERE device_id=$1`, deviceID,
	).Scan(&d.ID, &d.DeviceID, &d.DeviceName, &d.Model, &ownerID, &d.PairingCode, &d.IsOnline, &lastSeen, &d.CreatedAt)
	if ownerID.Valid { v := int(ownerID.Int64); d.OwnerID = &v }
	if lastSeen.Valid { d.LastSeenAt = &lastSeen.Time }
	return d, err
}

func (r *Repository) GetDeviceByID(id int) (*models.Device, error) {
	d := &models.Device{}
	var ownerID sql.NullInt64
	var lastSeen sql.NullTime
	err := r.db.QueryRow(
		`SELECT id,device_id,device_name,model,owner_id,pairing_code,is_online,last_seen_at,created_at FROM devices WHERE id=$1`, id,
	).Scan(&d.ID, &d.DeviceID, &d.DeviceName, &d.Model, &ownerID, &d.PairingCode, &d.IsOnline, &lastSeen, &d.CreatedAt)
	if ownerID.Valid { v := int(ownerID.Int64); d.OwnerID = &v }
	if lastSeen.Valid { d.LastSeenAt = &lastSeen.Time }
	return d, err
}

func (r *Repository) GetDevicesByOwnerID(ownerID int) ([]models.Device, error) {
	rows, err := r.db.Query(
		`SELECT id,device_id,device_name,model,owner_id,is_online,last_seen_at,created_at FROM devices WHERE owner_id=$1 ORDER BY created_at DESC`, ownerID,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var devices []models.Device
	for rows.Next() {
		var d models.Device
		var lastSeen sql.NullTime
		rows.Scan(&d.ID, &d.DeviceID, &d.DeviceName, &d.Model, &d.OwnerID, &d.IsOnline, &lastSeen, &d.CreatedAt)
		if lastSeen.Valid { d.LastSeenAt = &lastSeen.Time }
		devices = append(devices, d)
	}
	return devices, nil
}

func (r *Repository) BindDevice(deviceID string, ownerID int, deviceName, model, pairingCode string) (*models.Device, error) {
	d := &models.Device{}
	var lastSeen sql.NullTime
	err := r.db.QueryRow(
		`UPDATE devices SET owner_id=$2, device_name=CASE WHEN $3='' THEN device_name ELSE $3 END, model=CASE WHEN $4='' THEN model ELSE $4 END, pairing_code='' WHERE device_id=$1 AND owner_id IS NULL AND pairing_code=$5 RETURNING id,device_id,device_name,model,owner_id,pairing_code,is_online,last_seen_at,created_at`,
		deviceID, ownerID, deviceName, model, pairingCode,
	).Scan(&d.ID, &d.DeviceID, &d.DeviceName, &d.Model, &d.OwnerID, &d.PairingCode, &d.IsOnline, &lastSeen, &d.CreatedAt)
	if lastSeen.Valid { d.LastSeenAt = &lastSeen.Time }
	return d, err
}

func (r *Repository) UnbindDevice(id, ownerID int) error {
	_, err := r.db.Exec(`UPDATE devices SET owner_id=NULL,pairing_code=$3 WHERE id=$1 AND owner_id=$2`, id, ownerID, fmt.Sprintf("%06d", rand.Intn(1000000)))
	return err
}

func (r *Repository) UpdateDeviceOnline(deviceID string, online bool) {
	r.db.Exec(`UPDATE devices SET is_online=$2,last_seen_at=$3 WHERE device_id=$1`, deviceID, online, time.Now())
}

// ===== Rule =====

func (r *Repository) CreateRule(rule *models.Rule) error {
	return r.db.QueryRow(
		`INSERT INTO rules (name,owner_id,is_active) VALUES ($1,$2,$3) RETURNING id,created_at,updated_at`,
		rule.Name, rule.OwnerID, rule.IsActive,
	).Scan(&rule.ID, &rule.CreatedAt, &rule.UpdatedAt)
}

func (r *Repository) GetRuleByID(id int) (*models.Rule, error) {
	rule := &models.Rule{}
	err := r.db.QueryRow(
		`SELECT id,name,owner_id,is_active,created_at,updated_at FROM rules WHERE id=$1`, id,
	).Scan(&rule.ID, &rule.Name, &rule.OwnerID, &rule.IsActive, &rule.CreatedAt, &rule.UpdatedAt)
	return rule, err
}

func (r *Repository) GetRulesByOwnerID(ownerID int) ([]models.Rule, error) {
	rows, err := r.db.Query(
		`SELECT id,name,owner_id,is_active,created_at,updated_at FROM rules WHERE owner_id=$1 ORDER BY created_at DESC`, ownerID,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var rules []models.Rule
	for rows.Next() {
		var rule models.Rule
		rows.Scan(&rule.ID, &rule.Name, &rule.OwnerID, &rule.IsActive, &rule.CreatedAt, &rule.UpdatedAt)
		rules = append(rules, rule)
	}
	return rules, nil
}

func (r *Repository) UpdateRule(rule *models.Rule) error {
	_, err := r.db.Exec(
		`UPDATE rules SET name=$2,is_active=$3,updated_at=CURRENT_TIMESTAMP WHERE id=$1 AND owner_id=$4`,
		rule.ID, rule.Name, rule.IsActive, rule.OwnerID,
	)
	return err
}

func (r *Repository) DeleteRule(id, ownerID int) error {
	_, err := r.db.Exec(`DELETE FROM rules WHERE id=$1 AND owner_id=$2`, id, ownerID)
	return err
}

func (r *Repository) ToggleRule(id, ownerID int, isActive bool) error {
	_, err := r.db.Exec(`UPDATE rules SET is_active=$3,updated_at=CURRENT_TIMESTAMP WHERE id=$1 AND owner_id=$2`, id, ownerID, isActive)
	return err
}

// ===== RuleApp =====

func (r *Repository) SetRuleApps(ruleID int, apps []models.RuleApp) error {
	r.db.Exec(`DELETE FROM rule_apps WHERE rule_id=$1`, ruleID)
	for _, app := range apps {
		_, err := r.db.Exec(`INSERT INTO rule_apps (rule_id,package_name,app_name) VALUES ($1,$2,$3)`, ruleID, app.PackageName, app.AppName)
		if err != nil {
			return err
		}
	}
	return nil
}

func (r *Repository) GetRuleApps(ruleID int) ([]models.RuleApp, error) {
	rows, err := r.db.Query(`SELECT id,rule_id,package_name,app_name FROM rule_apps WHERE rule_id=$1`, ruleID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var apps []models.RuleApp
	for rows.Next() {
		var a models.RuleApp
		rows.Scan(&a.ID, &a.RuleID, &a.PackageName, &a.AppName)
		apps = append(apps, a)
	}
	return apps, nil
}

// ===== RuleSchedule =====

func (r *Repository) SetRuleSchedules(ruleID int, schedules []models.RuleSchedule) error {
	r.db.Exec(`DELETE FROM rule_schedules WHERE rule_id=$1`, ruleID)
	for _, s := range schedules {
		_, err := r.db.Exec(
			`INSERT INTO rule_schedules (rule_id,days_of_week,start_time,end_time) VALUES ($1,$2,$3,$4)`,
			ruleID, pq.Array(s.DaysOfWeek), s.StartTime, s.EndTime,
		)
		if err != nil {
			return err
		}
	}
	return nil
}

func (r *Repository) GetRuleSchedules(ruleID int) ([]models.RuleSchedule, error) {
	rows, err := r.db.Query(`SELECT id,rule_id,days_of_week,start_time,end_time FROM rule_schedules WHERE rule_id=$1`, ruleID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var schedules []models.RuleSchedule
	for rows.Next() {
		var s models.RuleSchedule
		var days []int
		rows.Scan(&s.ID, &s.RuleID, pq.Array(&days), &s.StartTime, &s.EndTime)
		s.DaysOfWeek = days
		schedules = append(schedules, s)
	}
	return schedules, nil
}

// ===== RuleDevice =====

func (r *Repository) SetRuleDevices(ruleID int, deviceIDs []int) error {
	r.db.Exec(`DELETE FROM rule_devices WHERE rule_id=$1`, ruleID)
	for _, did := range deviceIDs {
		_, err := r.db.Exec(`INSERT INTO rule_devices (rule_id,device_id) VALUES ($1,$2)`, ruleID, did)
		if err != nil {
			return err
		}
	}
	return nil
}

func (r *Repository) GetRuleDevices(ruleID int) ([]int, error) {
	rows, err := r.db.Query(`SELECT device_id FROM rule_devices WHERE rule_id=$1`, ruleID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var ids []int
	for rows.Next() {
		var id int
		rows.Scan(&id)
		ids = append(ids, id)
	}
	return ids, nil
}

// ===== Event =====

func (r *Repository) CreateEvents(events []models.Event) error {
	tx, err := r.db.Begin()
	if err != nil {
		return fmt.Errorf("事务开始失败: %w", err)
	}
	defer tx.Rollback()

	stmt, err := tx.Prepare(`INSERT INTO events (device_id,package_name,app_name,blocked_at) VALUES ($1,$2,$3,$4)`)
	if err != nil {
		return fmt.Errorf("预处理失败: %w", err)
	}
	defer stmt.Close()

	for _, e := range events {
		if _, err := stmt.Exec(e.DeviceID, e.PackageName, e.AppName, e.BlockedAt); err != nil {
			return fmt.Errorf("写入事件失败: %w", err)
		}
	}
	return tx.Commit()
}

func (r *Repository) GetEvents(ownerID int, filter models.EventFilter, page models.PaginationQuery) ([]models.Event, int, error) {
	where := "WHERE d.owner_id=$1"
	var args []interface{}
	args = append(args, ownerID)
	n := 2

	if filter.DeviceID > 0 {
		where += fmt.Sprintf(" AND e.device_id=$%d", n); args = append(args, filter.DeviceID); n++
	}
	if filter.PackageName != "" {
		where += fmt.Sprintf(" AND e.package_name=$%d", n); args = append(args, filter.PackageName); n++
	}
	if filter.StartDate != "" {
		where += fmt.Sprintf(" AND e.blocked_at>=$%d", n); args = append(args, filter.StartDate); n++
	}
	if filter.EndDate != "" {
		where += fmt.Sprintf(" AND e.blocked_at<=$%d", n); args = append(args, filter.EndDate); n++
	}

	var total int
	countQuery := fmt.Sprintf(`SELECT COUNT(*) FROM events e JOIN devices d ON e.device_id=d.id %s`, where)
	r.db.QueryRow(countQuery, args...).Scan(&total)

	offset := (page.Page - 1) * page.PageSize
	query := fmt.Sprintf(
		`SELECT e.id,e.device_id,e.package_name,e.app_name,e.blocked_at FROM events e JOIN devices d ON e.device_id=d.id %s ORDER BY e.blocked_at DESC LIMIT $%d OFFSET $%d`,
		where, n, n+1,
	)
	args = append(args, page.PageSize, offset)

	rows, err := r.db.Query(query, args...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	var events []models.Event
	for rows.Next() {
		var e models.Event
		rows.Scan(&e.ID, &e.DeviceID, &e.PackageName, &e.AppName, &e.BlockedAt)
		events = append(events, e)
	}
	return events, total, nil
}

func (r *Repository) GetStats(ownerID int) (*models.StatsResponse, error) {
	stats := &models.StatsResponse{}

	r.db.QueryRow(`SELECT COUNT(*) FROM events e JOIN devices d ON e.device_id=d.id WHERE d.owner_id=$1`, ownerID).Scan(&stats.TotalBlocked)
	r.db.QueryRow(`SELECT COUNT(*) FROM events e JOIN devices d ON e.device_id=d.id WHERE d.owner_id=$1 AND e.blocked_at>=CURRENT_DATE`, ownerID).Scan(&stats.TodayBlocked)

	rows, _ := r.db.Query(
		`SELECT e.package_name,e.app_name,COUNT(*) as cnt FROM events e JOIN devices d ON e.device_id=d.id WHERE d.owner_id=$1 GROUP BY e.package_name,e.app_name ORDER BY cnt DESC LIMIT 10`, ownerID,
	)
	if rows != nil {
		defer rows.Close()
		for rows.Next() {
			var a models.AppStat
			rows.Scan(&a.PackageName, &a.AppName, &a.Count)
			stats.TopApps = append(stats.TopApps, a)
		}
	}

	rows2, _ := r.db.Query(
		`SELECT DATE(e.blocked_at) as dt,COUNT(*) FROM events e JOIN devices d ON e.device_id=d.id WHERE d.owner_id=$1 AND e.blocked_at>=CURRENT_DATE-7 GROUP BY dt ORDER BY dt`, ownerID,
	)
	if rows2 != nil {
		defer rows2.Close()
		for rows2.Next() {
			var d models.DayStat
			rows2.Scan(&d.Date, &d.Count)
			stats.DailyStats = append(stats.DailyStats, d)
		}
	}
	return stats, nil
}

// ===== ChildApp =====

func (r *Repository) UpsertChildApps(deviceID int, apps []models.ChildAppItem) error {
	for _, a := range apps {
		_, err := r.db.Exec(
			`INSERT INTO child_apps (device_id,package_name,app_name) VALUES ($1,$2,$3) ON CONFLICT (device_id,package_name) DO UPDATE SET app_name=$3,synced_at=CURRENT_TIMESTAMP`,
			deviceID, a.PackageName, a.AppName,
		)
		if err != nil {
			return err
		}
	}
	return nil
}

func (r *Repository) GetChildApps(deviceID int, search string, page models.PaginationQuery) ([]models.ChildApp, int, error) {
	where := "WHERE device_id=$1"
	args := []interface{}{deviceID}
	n := 2

	if search != "" {
		where += fmt.Sprintf(" AND (package_name ILIKE $%d OR app_name ILIKE $%d)", n, n)
		args = append(args, "%"+search+"%")
		n++
	}

	var total int
	r.db.QueryRow(fmt.Sprintf(`SELECT COUNT(*) FROM child_apps %s`, where), args...).Scan(&total)

	offset := (page.Page - 1) * page.PageSize
	query := fmt.Sprintf(`SELECT id,device_id,package_name,app_name,synced_at FROM child_apps %s ORDER BY app_name LIMIT $%d OFFSET $%d`, where, n, n+1)
	args = append(args, page.PageSize, offset)

	rows, err := r.db.Query(query, args...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	var apps []models.ChildApp
	for rows.Next() {
		var a models.ChildApp
		rows.Scan(&a.ID, &a.DeviceID, &a.PackageName, &a.AppName, &a.SyncedAt)
		apps = append(apps, a)
	}
	return apps, total, nil
}

// ===== AppLog =====

func (r *Repository) InsertLogs(logs []models.AppLog) error {
	tx, err := r.db.Begin()
	if err != nil {
		return fmt.Errorf("事务开始失败: %w", err)
	}
	defer tx.Rollback()

	stmt, err := tx.Prepare(
		`INSERT INTO app_logs (source,device_id,owner_id,level,tag,message,stacktrace,logged_at) VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`,
	)
	if err != nil {
		return fmt.Errorf("预处理失败: %w", err)
	}
	defer stmt.Close()

	for _, l := range logs {
		if _, err := stmt.Exec(l.Source, l.DeviceID, l.OwnerID, l.Level, l.Tag, l.Message, l.Stacktrace, l.LoggedAt); err != nil {
			return fmt.Errorf("写入日志失败: %w", err)
		}
	}
	return tx.Commit()
}

func (r *Repository) QueryLogs(filter models.LogFilter, page models.PaginationQuery) ([]models.AppLog, int, error) {
	where := "WHERE 1=1"
	var args []interface{}
	n := 1

	if filter.Source != "" {
		where += fmt.Sprintf(" AND source=$%d", n); args = append(args, filter.Source); n++
	}
	if filter.Level != "" {
		where += fmt.Sprintf(" AND level=$%d", n); args = append(args, filter.Level); n++
	}
	if filter.Start != "" {
		where += fmt.Sprintf(" AND logged_at>=$%d", n); args = append(args, filter.Start); n++
	}
	if filter.End != "" {
		where += fmt.Sprintf(" AND logged_at<=$%d", n); args = append(args, filter.End); n++
	}

	var total int
	r.db.QueryRow(fmt.Sprintf(`SELECT COUNT(*) FROM app_logs %s`, where), args...).Scan(&total)

	offset := (page.Page - 1) * page.PageSize
	args = append(args, page.PageSize, offset)
	query := fmt.Sprintf(
		`SELECT id,source,device_id,owner_id,level,tag,message,stacktrace,logged_at,uploaded_at FROM app_logs %s ORDER BY logged_at DESC LIMIT $%d OFFSET $%d`,
		where, n, n+1,
	)

	rows, err := r.db.Query(query, args...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	var logs []models.AppLog
	for rows.Next() {
		var l models.AppLog
		var ownerID sql.NullInt64
		rows.Scan(&l.ID, &l.Source, &l.DeviceID, &ownerID, &l.Level, &l.Tag, &l.Message, &l.Stacktrace, &l.LoggedAt, &l.UploadedAt)
		if ownerID.Valid { v := int(ownerID.Int64); l.OwnerID = &v }
		logs = append(logs, l)
	}
	return logs, total, nil
}

func (r *Repository) CleanupOldLogs() {
	r.db.Exec(`DELETE FROM app_logs WHERE logged_at < CURRENT_DATE - 90`)
}

// ===== Child Config =====

type ChildRuleOutput struct {
	ID        int                  `json:"id"`
	Name      string               `json:"name"`
	Apps      []string             `json:"apps"`
	Schedules []models.RuleSchedule `json:"schedules"`
}

type ChildConfig struct {
	DeviceID string            `json:"device_id"`
	Rules    []ChildRuleOutput `json:"rules"`
}

func (r *Repository) GetChildConfig(deviceID int) (*ChildConfig, error) {
	device, err := r.GetDeviceByID(deviceID)
	if err != nil {
		return nil, err
	}

	rows, err := r.db.Query(
		`SELECT r.id,r.name FROM rules r JOIN rule_devices rd ON r.id=rd.rule_id WHERE rd.device_id=$1 AND r.is_active=true`, deviceID,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var rules []ChildRuleOutput
	for rows.Next() {
		var rule ChildRuleOutput
		rows.Scan(&rule.ID, &rule.Name)

		apps, _ := r.GetRuleApps(rule.ID)
		for _, a := range apps {
			rule.Apps = append(rule.Apps, a.PackageName)
		}
		rule.Schedules, _ = r.GetRuleSchedules(rule.ID)
		rules = append(rules, rule)
	}

	return &ChildConfig{DeviceID: device.DeviceID, Rules: rules}, nil
}
