export class ChartRenderer {
    constructor(canvasId) {
        const ctx = document.getElementById(canvasId).getContext('2d');

        Chart.defaults.color = '#a0a0a0';
        Chart.defaults.borderColor = 'rgba(255, 255, 255, 0.1)';

        this.heartIcon = document.getElementById('heart-icon');
        this.bpmText = document.getElementById('current-bpm');


        this.hrChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: [],
                datasets: [{
                    label: 'Frequência Cardíaca (BPM)',
                    data: [],
                    borderColor: '#ff2a55', // Vermelho neon estilo monitor cardíaco
                    backgroundColor: 'rgba(255, 42, 85, 0.15)',
                    borderWidth: 2,
                    tension: 0.4,
                    fill: true,
                    pointRadius: 2,
                    pointHoverRadius: 6
                }]
            },
            options: {
                responsive: true,
                animation: {
                    duration: 400,
                    easing: 'easeOutCubic',
                    loop: false

                }, // Mantido em 0 para controle manual
                scales: {
                    x: {
                    title: { display: true, text: 'Tempo (segundos)' },
                    grid: { color: 'rgba(255, 255, 255, 0.05)' }
                    },
                    y: {
                        title: { display: true, text: 'BPM' },
                        suggestedMin: 60,
                        suggestedMax: 180
                    }
                },
                plugins: {
                    zoom: {
                        pan: {
                            enabled: true,
                            mode: 'x', // Permite arrastar o gráfico apenas horizontalmente
                         },
                        zoom: {
                            wheel: {
                                enabled: true, // Zoom pelo scroll do mouse
                            },
                            pinch: {
                                enabled: true // Zoom pelo movimento de pinça (touchpads/celulares)
                            },
                            mode: 'x', // Aplica o zoom apenas na linha do tempo
                        },
                        limits: {
                            x: {
                                minRange: 2, // e.g., minimum range of 5 days
                            }
                        }
                    }
                }
            }
        });
    }

    renderData(time, bpm, isRecovered = false) {
        console.log(`${isRecovered ? "Received [RECOVERED]" : "Received"} Time: ${time}, BPM: ${bpm}`);
        this.hrChart.data.labels.push(time);
        this.hrChart.data.datasets[0].data.push(bpm);
        this.hrChart.update();

        this.updateHeartBeat(bpm);
    }

    updateHeartBeat(bpm)
    {
        if(!this.heartIcon || !this.bpmText) return;

        this.bpmText.innerText = bpm;
        const duration = 60/bpm;
        this.heartIcon.style.animation = `heartbeat ${duration}s infinite`
    }

    clear() {
        this.hrChart.data.labels = [];
        this.hrChart.data.datasets[0].data = [];
        this.hrChart.resetZoom(); // Volta o zoom para a estaca zero
        this.hrChart.update();

        if(this.bpmText) this.bpmText.innerText = "--";
        if(this.heartIcon) this.heartIcon.style.animation = "none";
    }

}