import { useEffect, useRef } from 'react';
import * as THREE from 'three';
import gsap from 'gsap';

export function SpiralAnimation() {
  const containerRef = useRef<HTMLDivElement>(null);
  const cleanupRef = useRef<(() => void) | null>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    // Scene setup
    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(
      60,
      container.clientWidth / container.clientHeight,
      0.1,
      1000,
    );
    camera.position.z = 30;

    const renderer = new THREE.WebGLRenderer({
      antialias: true,
      alpha: true,
    });
    renderer.setSize(container.clientWidth, container.clientHeight);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.setClearColor(0x000000, 1);
    container.appendChild(renderer.domElement);

    // Create spiral particles
    const particleCount = 3000;
    const positions = new Float32Array(particleCount * 3);
    const colors = new Float32Array(particleCount * 3);
    const sizes = new Float32Array(particleCount);

    for (let i = 0; i < particleCount; i++) {
      const t = (i / particleCount) * Math.PI * 12;
      const radius = 2 + t * 0.5;
      const y = (i / particleCount - 0.5) * 30;

      positions[i * 3] = Math.cos(t) * radius;
      positions[i * 3 + 1] = y;
      positions[i * 3 + 2] = Math.sin(t) * radius;

      // Purple to indigo gradient
      const hue = 0.72 + (i / particleCount) * 0.08;
      const color = new THREE.Color().setHSL(hue, 0.8, 0.6);
      colors[i * 3] = color.r;
      colors[i * 3 + 1] = color.g;
      colors[i * 3 + 2] = color.b;

      sizes[i] = Math.random() * 2 + 0.5;
    }

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3));
    geometry.setAttribute('size', new THREE.BufferAttribute(sizes, 1));

    const material = new THREE.PointsMaterial({
      size: 0.08,
      vertexColors: true,
      transparent: true,
      opacity: 0.8,
      blending: THREE.AdditiveBlending,
      sizeAttenuation: true,
    });

    const points = new THREE.Points(geometry, material);
    scene.add(points);

    // Add ambient glow rings
    for (let r = 0; r < 5; r++) {
      const ringGeometry = new THREE.RingGeometry(
        4 + r * 3.5,
        4.1 + r * 3.5,
        128,
      );
      const ringMaterial = new THREE.MeshBasicMaterial({
        color: new THREE.Color().setHSL(0.75, 0.6, 0.3),
        transparent: true,
        opacity: 0.1 - r * 0.015,
        side: THREE.DoubleSide,
      });
      const ring = new THREE.Mesh(ringGeometry, ringMaterial);
      ring.rotation.x = Math.PI / 2;
      scene.add(ring);
    }

    // GSAP animation
    const state = { rotationY: 0 };
    gsap.to(state, {
      rotationY: Math.PI * 2,
      duration: 40,
      repeat: -1,
      ease: 'none',
    });

    // Render loop
    let animId: number;
    const clock = new THREE.Clock();

    function animate() {
      animId = requestAnimationFrame(animate);
      const elapsed = clock.getElapsedTime();

      points.rotation.y = state.rotationY;
      points.rotation.x = Math.sin(elapsed * 0.1) * 0.1;

      // Gentle camera bob
      camera.position.x = Math.sin(elapsed * 0.15) * 2;
      camera.position.y = Math.cos(elapsed * 0.1) * 1.5;
      camera.lookAt(0, 0, 0);

      renderer.render(scene, camera);
    }
    animate();

    // Resize handler
    function handleResize() {
      if (!container) return;
      camera.aspect = container.clientWidth / container.clientHeight;
      camera.updateProjectionMatrix();
      renderer.setSize(container.clientWidth, container.clientHeight);
    }
    window.addEventListener('resize', handleResize);

    cleanupRef.current = () => {
      cancelAnimationFrame(animId);
      window.removeEventListener('resize', handleResize);
      gsap.killTweensOf(state);
      geometry.dispose();
      material.dispose();
      renderer.dispose();
      if (container.contains(renderer.domElement)) {
        container.removeChild(renderer.domElement);
      }
    };

    return () => {
      cleanupRef.current?.();
    };
  }, []);

  return (
    <div
      ref={containerRef}
      className="absolute inset-0 w-full h-full"
      style={{ background: 'black' }}
    />
  );
}
