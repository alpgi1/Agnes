import { useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { LandingScreen } from '@/screens/LandingScreen';
import { ChatScreen } from '@/screens/ChatScreen';

type Screen = 'landing' | 'chat';

export default function App() {
  const [screen, setScreen] = useState<Screen>('landing');

  return (
    <AnimatePresence mode="wait">
      {screen === 'landing' ? (
        <motion.div
          key="landing"
          initial={{ opacity: 1 }}
          exit={{ opacity: 0, scale: 1.1 }}
          transition={{ duration: 0.8, ease: 'easeInOut' }}
          className="absolute inset-0"
        >
          <LandingScreen onEnter={() => setScreen('chat')} />
        </motion.div>
      ) : (
        <motion.div
          key="chat"
          initial={{ opacity: 0, scale: 1.05 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.8, ease: 'easeOut' }}
          className="absolute inset-0"
        >
          <ChatScreen />
        </motion.div>
      )}
    </AnimatePresence>
  );
}
