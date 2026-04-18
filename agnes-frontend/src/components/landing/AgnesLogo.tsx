import { motion } from 'framer-motion';

interface AgnesLogoProps {
  onEnter: () => void;
  visible: boolean;
}

export function AgnesLogo({ onEnter, visible }: AgnesLogoProps) {
  return (
    <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-10">
      <motion.button
        onClick={onEnter}
        className="text-white text-5xl md:text-7xl tracking-[0.3em] uppercase font-extralight
                   transition-all duration-700 hover:tracking-[0.4em]
                   cursor-pointer select-none border-none bg-transparent outline-none"
        initial={{ opacity: 0, y: 20 }}
        animate={visible ? { opacity: 1, y: 0 } : { opacity: 0, y: 20 }}
        transition={{ duration: 1.5, ease: 'easeOut' }}
        style={{ fontFamily: 'Inter, sans-serif' }}
      >
        AGNES
      </motion.button>
      <motion.p
        className="mt-6 text-center text-white/40 text-sm tracking-widest uppercase"
        initial={{ opacity: 0 }}
        animate={visible ? { opacity: 1 } : { opacity: 0 }}
        transition={{ duration: 1, delay: 0.5 }}
      >
        AI Supply Chain Manager
      </motion.p>
    </div>
  );
}
