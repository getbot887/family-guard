package main

import (
	"database/sql"
	"fmt"
	"log"
	"os"

	"family-guard-backend/internal/handlers"
	"family-guard-backend/internal/middleware"
	"family-guard-backend/internal/repository"

	"github.com/gin-gonic/gin"
	_ "github.com/lib/pq"
	"github.com/robfig/cron/v3"
	"golang.org/x/crypto/bcrypt"
)

func main() {
	dbHost := getEnv("DB_HOST", "localhost")
	dbPort := getEnv("DB_PORT", "5432")
	dbUser := getEnv("DB_USER", "postgres")
	dbPass := getEnv("DB_PASSWORD", "postgres")
	dbName := getEnv("DB_NAME", "family_guard")
	jwtSecret := getEnv("JWT_SECRET", "change-me-in-production")
	if jwtSecret == "change-me-in-production" && os.Getenv("GIN_MODE") != "debug" {
		log.Fatal("安全错误: 生产环境必须设置 JWT_SECRET 环境变量")
	}
	port := getEnv("SERVER_PORT", "8080")
	corsOrigin := getEnv("CORS_ORIGIN", "*")

	db := mustConnect(dbHost, dbPort, dbUser, dbPass, dbName)
	mustMigrate(db)
	seedDefaultUser(db)
	repo := repository.New(db)
	handler := handlers.New(repo, jwtSecret)

	// 定时清理旧日志
	c := cron.New()
	if _, err := c.AddFunc("0 3 * * *", func() { repo.CleanupOldLogs() }); err != nil {
		log.Printf("cron 初始化警告: %v", err)
	}
	c.Start()

	r := gin.Default()
	r.Use(cors(corsOrigin))

	r.GET("/health", handler.Health)

	api := r.Group("/api/v1")
	{
		api.POST("/register", handler.Register)
		api.POST("/login", handler.Login)
		api.POST("/child/register", handler.ChildRegister) // 孩子端注册（无需认证）
	}

	parent := api.Group("")
	parent.Use(middleware.ParentAuth(jwtSecret))
	{
		parent.GET("/devices", handler.GetDevices)
		parent.POST("/devices/bind", handler.BindDevice)
		parent.DELETE("/devices/:id", handler.UnbindDevice)
		parent.GET("/devices/:id/apps", handler.GetDeviceApps)

		parent.GET("/rules", handler.GetRules)
		parent.POST("/rules", handler.CreateRule)
		parent.PUT("/rules/:id", handler.UpdateRule)
		parent.DELETE("/rules/:id", handler.DeleteRule)
		parent.POST("/rules/:id/toggle", handler.ToggleRule)

		parent.GET("/events", handler.GetEvents)
		parent.GET("/events/stats", handler.GetStats)

		parent.GET("/logs", handler.QueryLogs)
		parent.GET("/logs/export", handler.ExportLogs)
	}

	child := api.Group("/child")
	child.Use(middleware.ChildAuth(jwtSecret))
	{
		child.GET("/config", handler.ChildGetConfig)
		child.POST("/apps", handler.ChildReportApps)
		child.POST("/events", handler.ChildReportEvents)
		child.POST("/heartbeat", handler.ChildHeartbeat)
		child.POST("/logs", handler.UploadLogs)
	}

	// 日志上传 — 家长端路由
	parent.POST("/logs", handler.UploadLogs)

	// Web 管理后台 SPA
	webDir := os.Getenv("WEB_ADMIN_DIR")
	if webDir == "" {
		webDir = "/home/dell/projects/app/web-admin"
	}
	if _, err := os.Stat(webDir); err == nil {
		r.Static("/web", webDir)
		r.StaticFile("/style.css", webDir+"/style.css")
		r.Static("/js", webDir+"/js")
		r.GET("/", func(c *gin.Context) {
			c.File(webDir + "/index.html")
		})
	} else {
		r.GET("/", func(c *gin.Context) {
			c.JSON(200, gin.H{"service": "FamilyGuard", "status": "running"})
		})
	}

	log.Printf("FamilyGuard 后端启动在 :%s", port)
	r.Run(":" + port)
}

func mustConnect(host, port, user, pass, name string) *sql.DB {
	dsn := fmt.Sprintf("host=%s port=%s user=%s password=%s dbname=%s sslmode=disable", host, port, user, pass, name)
	db, err := sql.Open("postgres", dsn)
	if err != nil {
		log.Fatalf("数据库连接失败: %v", err)
	}
	if err := db.Ping(); err != nil {
		log.Fatalf("数据库ping失败: %v", err)
	}
	fmt.Println("数据库连接成功")
	return db
}

func mustMigrate(db *sql.DB) {
	queries := []string{
		`CREATE TABLE IF NOT EXISTS users (id SERIAL PRIMARY KEY,email VARCHAR(255) UNIQUE NOT NULL,password_hash VARCHAR(255) NOT NULL,nickname VARCHAR(100),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)`,
		`CREATE TABLE IF NOT EXISTS devices (id SERIAL PRIMARY KEY,device_id VARCHAR(255) UNIQUE NOT NULL,device_name VARCHAR(100),model VARCHAR(100),owner_id INTEGER REFERENCES users(id),pairing_code VARCHAR(10),is_online BOOLEAN DEFAULT FALSE,last_seen_at TIMESTAMP,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)`,
		`CREATE TABLE IF NOT EXISTS rules (id SERIAL PRIMARY KEY,name VARCHAR(100) NOT NULL,owner_id INTEGER REFERENCES users(id),is_active BOOLEAN DEFAULT TRUE,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)`,
		`CREATE TABLE IF NOT EXISTS rule_apps (id SERIAL PRIMARY KEY,rule_id INTEGER REFERENCES rules(id) ON DELETE CASCADE,package_name VARCHAR(255) NOT NULL,app_name VARCHAR(100))`,
		`CREATE TABLE IF NOT EXISTS rule_schedules (id SERIAL PRIMARY KEY,rule_id INTEGER REFERENCES rules(id) ON DELETE CASCADE,days_of_week INTEGER[] NOT NULL,start_time TIME NOT NULL,end_time TIME NOT NULL)`,
		`CREATE TABLE IF NOT EXISTS rule_devices (rule_id INTEGER REFERENCES rules(id) ON DELETE CASCADE,device_id INTEGER REFERENCES devices(id) ON DELETE CASCADE,PRIMARY KEY (rule_id,device_id))`,
		`CREATE TABLE IF NOT EXISTS events (id SERIAL PRIMARY KEY,device_id INTEGER REFERENCES devices(id),package_name VARCHAR(255) NOT NULL,app_name VARCHAR(100),blocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)`,
		`CREATE TABLE IF NOT EXISTS child_apps (id SERIAL PRIMARY KEY,device_id INTEGER REFERENCES devices(id) ON DELETE CASCADE,package_name VARCHAR(255) NOT NULL,app_name VARCHAR(100) NOT NULL,synced_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,UNIQUE(device_id,package_name))`,
		`CREATE TABLE IF NOT EXISTS app_logs (id BIGSERIAL PRIMARY KEY,source VARCHAR(20) NOT NULL,device_id VARCHAR(255),owner_id INTEGER REFERENCES users(id),level VARCHAR(10) NOT NULL,tag VARCHAR(100) NOT NULL,message TEXT NOT NULL,stacktrace TEXT,logged_at TIMESTAMP NOT NULL,uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)`,
	}
	for _, q := range queries {
		if _, err := db.Exec(q); err != nil {
			log.Printf("迁移警告: %v", err)
		}
	}

	indexes := []string{
		`CREATE INDEX IF NOT EXISTS idx_devices_owner_id ON devices(owner_id)`,
		`CREATE INDEX IF NOT EXISTS idx_rules_owner_id ON rules(owner_id)`,
		`CREATE INDEX IF NOT EXISTS idx_events_blocked_at ON events(blocked_at DESC)`,
		`CREATE INDEX IF NOT EXISTS idx_events_device_blocked ON events(device_id,blocked_at DESC)`,
		`CREATE INDEX IF NOT EXISTS idx_child_apps_device_id ON child_apps(device_id)`,
		`CREATE INDEX IF NOT EXISTS idx_app_logs_source_level ON app_logs(source,level,logged_at DESC)`,
	}
	for _, q := range indexes {
		db.Exec(q)
	}
	fmt.Println("数据库迁移完成")
}

func seedDefaultUser(db *sql.DB) {
	email := getEnv("DEFAULT_EMAIL", "admin@familyguard.com")
	password := getEnv("DEFAULT_PASSWORD", "admin123456")
	nickname := getEnv("DEFAULT_NICKNAME", "管理员")

	var exists int
	db.QueryRow(`SELECT COUNT(*) FROM users WHERE email=$1`, email).Scan(&exists)
	if exists > 0 {
		return
	}

	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		log.Printf("预置用户密码加密失败: %v", err)
		return
	}
	_, err = db.Exec(`INSERT INTO users (email, password_hash, nickname) VALUES ($1, $2, $3)`, email, string(hash), nickname)
	if err != nil {
		log.Printf("创建预置用户失败: %v", err)
		return
	}
	log.Printf("已创建预置账号: %s / %s", email, password)
}

func cors(allowedOrigin string) gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Header("Access-Control-Allow-Origin", allowedOrigin)
		c.Header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS")
		c.Header("Access-Control-Allow-Headers", "Authorization,X-Device-Token,Content-Type")
		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(204)
			return
		}
		c.Next()
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
