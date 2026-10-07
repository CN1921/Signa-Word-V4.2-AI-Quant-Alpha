CREATE TABLE IF NOT EXISTS keyword_stats (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  keyword VARCHAR(255) NOT NULL,
  stat_date DATE NOT NULL,
  mentions BIGINT DEFAULT 0,
  score DOUBLE DEFAULT 0,
  extra JSON NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY unique_keyword_date (keyword, stat_date),
  INDEX idx_stat_date (stat_date),
  INDEX idx_keyword (keyword)
);
