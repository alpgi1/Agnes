import { useEffect, useRef } from 'react';
import * as THREE from 'three';

export function DottedSurface() {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(
      60,
      container.clientWidth / container.clientHeight,
      0.1,
      1000,
    );
    camera.position.set(0, 15, 25);
    camera.lookAt(0, 0, 0);

    const renderer = new THREE.WebGLRenderer({
      antialias: true,
      alpha: true,
    });
    renderer.setSize(container.clientWidth, container.clientHeight);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.setClearColor(0x000000, 0);
    container.appendChild(renderer.domElement);

    // Create grid of dots
    const gridSize = 60;
    const spacing = 1.2;
    const dotCount = gridSize * gridSize;
    const positions = new Float32Array(dotCount * 3);
    const basePositions = new Float32Array(dotCount * 3);
    const colors = new Float32Array(dotCount * 3);

    for (let i = 0; i < gridSize; i++) {
      for (let j = 0; j < gridSize; j++) {
        const idx = (i * gridSize + j) * 3;
        const x = (i - gridSize / 2) * spacing;
        const z = (j - gridSize / 2) * spacing;

        positions[idx] = x;
        positions[idx + 1] = 0;
        positions[idx + 2] = z;

        basePositions[idx] = x;
        basePositions[idx + 1] = 0;
        basePositions[idx + 2] = z;

        // Distance-based color: center is purple, edges fade
        const dist = Math.sqrt(x * x + z * z) / (gridSize * spacing * 0.5);
        const hue = 0.72 + dist * 0.05;
        const lightness = 0.4 - dist * 0.15;
        const color = new THREE.Color().setHSL(hue, 0.5, Math.max(lightness, 0.1));
        colors[idx] = color.r;
        colors[idx + 1] = color.g;
        colors[idx + 2] = color.b;
      }
    }

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3));

    const material = new THREE.PointsMaterial({
      size: 0.06,
      vertexColors: true,
      transparent: true,
      opacity: 0.6,
      sizeAttenuation: true,
    });

    const dotGrid = new THREE.Points(geometry, material);
    scene.add(dotGrid);

    // Animation
    let animId: number;
    const clock = new THREE.Clock();

    function animate() {
      animId = requestAnimationFrame(animate);
      const elapsed = clock.getElapsedTime();
      const posArray = geometry.attributes.position.array as Float32Array;

      for (let i = 0; i < gridSize; i++) {
        for (let j = 0; j < gridSize; j++) {
          const idx = (i * gridSize + j) * 3;
          const x = basePositions[idx];
          const z = basePositions[idx + 2];

          // Wave displacement
          const wave1 = Math.sin(x * 0.3 + elapsed * 0.8) * 0.8;
          const wave2 = Math.cos(z * 0.25 + elapsed * 0.6) * 0.6;
          const wave3 = Math.sin((x + z) * 0.2 + elapsed * 0.4) * 0.4;

          posArray[idx + 1] = wave1 + wave2 + wave3;
        }
      }
      geometry.attributes.position.needsUpdate = true;

      // Slow camera orbit
      camera.position.x = Math.sin(elapsed * 0.05) * 5;
      camera.lookAt(0, 0, 0);

      renderer.render(scene, camera);
    }
    animate();

    function handleResize() {
      if (!container) return;
      camera.aspect = container.clientWidth / container.clientHeight;
      camera.updateProjectionMatrix();
      renderer.setSize(container.clientWidth, container.clientHeight);
    }
    window.addEventListener('resize', handleResize);

    return () => {
      cancelAnimationFrame(animId);
      window.removeEventListener('resize', handleResize);
      geometry.dispose();
      material.dispose();
      renderer.dispose();
      if (container.contains(renderer.domElement)) {
        container.removeChild(renderer.domElement);
      }
    };
  }, []);

  return (
    <div
      ref={containerRef}
      className="absolute inset-0 w-full h-full"
    />
  );
}
