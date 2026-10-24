output "public_ip" {
  description = "Public address of the telemetry instance."
  value       = aws_eip.app.public_ip
}

output "dashboard_url" {
  description = "Live alert dashboard."
  value       = "http://${aws_eip.app.public_ip}/"
}

output "ssh_command" {
  description = "SSH command for the instance."
  value       = "ssh ec2-user@${aws_eip.app.public_ip}"
}
