import MDXComponents from '@theme-original/MDXComponents'
import { Icon } from '@iconify/react'

export default {
  // Re-use the default mapping
  ...MDXComponents,
  icon: Icon // Make the iconify Icon component available in MDX as <icon />.
}
