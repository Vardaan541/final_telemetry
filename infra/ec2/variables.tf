variable "aws_region" {
  description = "Region for the telemetry instance."
  type        = string
  default     = "ap-south-1"
}

variable "instance_type" {
  description = "EC2 size. t3.small is enough for Postgres, Kafka, and the API when swap is enabled."
  type        = string
  default     = "t3.small"
}

variable "key_name" {
  description = "Name of the EC2 key pair created for SSH."
  type        = string
  default     = "vehicle-telemetry"
}

variable "public_key_path" {
  description = "Local public key uploaded to the EC2 key pair."
  type        = string
}

variable "ssh_cidr" {
  description = "CIDR allowed to open an SSH session."
  type        = string
  default     = "0.0.0.0/0"
}

variable "api_cidr" {
  description = "CIDR allowed to open the dashboard and API."
  type        = string
  default     = "0.0.0.0/0"
}
