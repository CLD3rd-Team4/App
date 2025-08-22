interface LogoProps {
  size?: "small" | "medium" | "large"
  className?: string
}

export default function Logo({ size = "medium", className = "" }: LogoProps) {
  const sizeClasses = {
    small: "text-xl",
    medium: "text-2xl",
    large: "text-4xl",
  }

  return (
    <div className={`font-bold text-blue-600 ${sizeClasses[size]} ${className}`}>
      <span className="bg-gradient-to-r from-blue-600 to-blue-800 bg-clip-text text-transparent">map.zip</span>
    </div>
  )
}
